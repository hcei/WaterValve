import Foundation
import XCTest
@testable import WaterValveLogic

final class ValveBridgeLogicTests: XCTestCase {
    func testBuildValveURLReturnsDirectHTTPURL() {
        let url = ValveBridgeLogic.buildValveURL(
            deviceId: "ignored",
            qrURL: "https://ykt.hgu.edu.cn/uwc_webapp/#/openValve?deviceId=abc"
        )

        XCTAssertEqual(url?.absoluteString, "https://ykt.hgu.edu.cn/uwc_webapp/#/openValve?deviceId=abc")
    }

    func testBuildValveURLFallsBackToSpaRouteWithEncodedQRPayload() {
        let url = ValveBridgeLogic.buildValveURL(
            deviceId: "device-md5",
            qrURL: "valve id/中文"
        )

        XCTAssertEqual(
            url?.absoluteString,
            "https://ykt.hgu.edu.cn/uwc_webapp/#/openValve?deviceId=valve%20id/%E4%B8%AD%E6%96%87"
        )
    }

    func testBuildValveURLFallsBackToBaseRouteWhenQRPayloadIsEmpty() {
        let url = ValveBridgeLogic.buildValveURL(deviceId: "device-md5", qrURL: "")

        XCTAssertEqual(url?.absoluteString, "https://ykt.hgu.edu.cn/uwc_webapp/#/openValve")
    }

    func testBuildTokenInjectionScriptIncludesRequiredStorageKeysAndBridgeValues() {
        let script = ValveBridgeLogic.buildTokenInjectionScript(
            session: ValveSessionSnapshot(
                userId: "user-1",
                accNum: "acc-2",
                epId: "ep-3",
                perCode: "per-4",
                uisToken: "uis-5",
                uwcToken: "uwc-6"
            )
        )

        XCTAssertTrue(script.contains("localStorage.setItem('uwcToken','uwc-6')"))
        XCTAssertTrue(script.contains("localStorage.setItem('uisToken','uis-5')"))
        XCTAssertTrue(script.contains("localStorage.setItem('uiastoken','uis-5')"))
        XCTAssertTrue(script.contains("localStorage.setItem('uwcAccNum','acc-2')"))
        XCTAssertTrue(script.contains("localStorage.setItem('uwcEpid','ep-3')"))
        XCTAssertTrue(script.contains("localStorage.setItem('uwcUserId','user-1')"))
        XCTAssertTrue(script.contains("localStorage.setItem('uwcPerCode','per-4')"))
        XCTAssertTrue(script.contains("localStorage.setItem('wxMark','1')"))
        XCTAssertTrue(script.contains("localStorage.setItem('isSdk',JSON.stringify(true))"))
        XCTAssertTrue(script.contains("window.__valveBridge={uwcToken:'uwc-6'"))
        XCTAssertTrue(script.contains("window.__valveBridge.token=window.__valveBridge.uwcToken"))
        XCTAssertTrue(script.contains("window.__waterValveBridge={scan:function()"))
    }

    func testParsePayloadFromH5CallURL() {
        let encodedJSON = "%7B%22action%22%3A%22openScan%22%2C%22callback%22%3A%22sendScanInfo%22%7D"
        let url = URL(string: "com.hzsun.h5call://bridge?paramjson=\(encodedJSON)")!

        let payload = ValveBridgePayload.parse(url: url)

        XCTAssertEqual(payload, ValveBridgePayload(action: "openScan", callback: "sendScanInfo"))
    }

    func testParseScriptMessageSupportsDictionaryAndJSONString() {
        let dictionaryPayload = ValveBridgeLogic.parseScriptMessage([
            "event": "valveOpened",
            "timestamp": "123"
        ])
        let stringPayload = ValveBridgeLogic.parseScriptMessage("{\"event\":\"openScan\",\"callback\":\"cb\"}")

        XCTAssertEqual(dictionaryPayload["event"] as? String, "valveOpened")
        XCTAssertEqual(dictionaryPayload["timestamp"] as? String, "123")
        XCTAssertEqual(stringPayload["event"] as? String, "openScan")
        XCTAssertEqual(stringPayload["callback"] as? String, "cb")
    }

    func testBuildScanResultEventScriptPublishesStructuredPayload() {
        let script = ValveBridgeLogic.buildScanResultEventScript(result: "qr-1")

        XCTAssertTrue(script.contains("window.__waterValveLastScan='qr-1'"))
        XCTAssertTrue(script.contains("window.__waterValveLastScanPayload=payload"))
        XCTAssertTrue(script.contains("content:'qr-1'"))
        XCTAssertTrue(script.contains("result:'qr-1'"))
        XCTAssertTrue(script.contains("success:true"))
        XCTAssertTrue(script.contains("CustomEvent('waterValveScanResult'"))
    }

    func testBuildScanCallbackScriptPassesStructuredPayloadToDirectAndNestedCallbacks() {
        let script = ValveBridgeLogic.buildScanCallbackScript(callback: "bridge.scan.done", result: "qr-2")

        XCTAssertTrue(script.contains("var callbackName='bridge.scan.done'"))
        XCTAssertTrue(script.contains("var payload={content:'qr-2',result:'qr-2',success:true};"))
        XCTAssertTrue(script.contains("target(payload);"))
        XCTAssertTrue(script.contains("scope(payload);"))
    }

    func testStringValueNormalizesSupportedInputs() {
        XCTAssertEqual(ValveBridgeLogic.stringValue("text"), "text")
        XCTAssertEqual(ValveBridgeLogic.stringValue(NSNumber(value: 12)), "12")
        XCTAssertEqual(ValveBridgeLogic.stringValue(NSNumber(value: 12.5)), "12.5")
        XCTAssertEqual(ValveBridgeLogic.stringValue(nil), "")
    }
}
