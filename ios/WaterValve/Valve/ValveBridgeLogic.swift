import Foundation

struct ValveSessionSnapshot: Equatable {
    let userId: String
    let accNum: String
    let epId: String
    let perCode: String
    let uisToken: String
    let uwcToken: String
}

struct ValveBridgePayload: Equatable {
    let action: String
    let callback: String

    static func parse(url: URL) -> ValveBridgePayload? {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let paramJSON = components.queryItems?.first(where: { $0.name == "paramjson" })?.value,
              let decoded = paramJSON.removingPercentEncoding,
              let data = decoded.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }

        return ValveBridgePayload(
            action: object["action"] as? String ?? "",
            callback: object["callback"] as? String ?? ""
        )
    }
}

enum ValveBridgeLogic {
    static func buildValveURL(deviceId: String, qrURL: String) -> URL? {
        if let direct = URL(string: qrURL),
           let scheme = direct.scheme,
           scheme.hasPrefix("http") {
            return direct
        }

        if !qrURL.isEmpty {
            let encoded = qrURL.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? deviceId
            return URL(string: "\(AppConstants.uwcSpaBaseURL.absoluteString)#/openValve?deviceId=\(encoded)")
        }

        return URL(string: "\(AppConstants.uwcSpaBaseURL.absoluteString)#/openValve")
    }

    static func buildTokenInjectionScript(session: ValveSessionSnapshot) -> String {
        var script = "(function(){try{"
        script += "if(!window.wx||!window.wx.ready){window.wx={ready:function(cb){if(cb)cb()},config:function(){},error:function(){},checkJsApi:function(opts){if(opts&&opts.success)opts.success({checkResult:{}})},invoke:function(){}};}"
        script += "window.__waterValveBridge={scan:function(){window.location.href='com.hzsun.h5call://bridge?paramjson='+encodeURIComponent(JSON.stringify({action:'openScan',callback:'nativeScan'}));}};"
        script += "window.__valveBridge={uwcToken:'\(escape(session.uwcToken))',uisToken:'\(escape(session.uisToken))',uiastoken:'\(escape(session.uisToken))',uwcAccNum:'\(escape(session.accNum))',uwcEpid:'\(escape(session.epId))',uwcUserId:'\(escape(session.userId))',uwcPerCode:'\(escape(session.perCode))',wxMark:'1',isSdk:true};"
        script += "window.__valveBridge.token=window.__valveBridge.uwcToken;"
        script += "window.__valveBridge.userId=window.__valveBridge.uwcUserId;"
        script += "localStorage.setItem('uwcToken','\(escape(session.uwcToken))');"
        script += "localStorage.setItem('uisToken','\(escape(session.uisToken))');"
        script += "localStorage.setItem('uiastoken','\(escape(session.uisToken))');"
        script += "localStorage.setItem('uwcAccNum','\(escape(session.accNum))');"
        script += "localStorage.setItem('uwcEpid','\(escape(session.epId))');"
        script += "localStorage.setItem('uwcUserId','\(escape(session.userId))');"
        script += "localStorage.setItem('uwcPerCode','\(escape(session.perCode))');"
        script += "localStorage.setItem('wxMark','1');"
        script += "localStorage.setItem('isSdk',JSON.stringify(true));"
        script += "}catch(e){console.error('[WaterValve iOS] '+e.message);}})();"
        return script
    }

    static func buildScanResultEventScript(result: String) -> String {
        let escapedResult = escape(result)
        return """
        (function(){
            var payload={content:'\(escapedResult)',result:'\(escapedResult)',success:true};
            window.__waterValveLastScan='\(escapedResult)';
            window.__waterValveLastScanPayload=payload;
            window.dispatchEvent(new CustomEvent('waterValveScanResult',{detail:payload}));
        })();
        """
    }

    static func buildScanCallbackScript(callback: String, result: String) -> String {
        let escapedCallback = escape(callback)
        let escapedResult = escape(result)
        return """
        (function(){
            try{
                var callbackName='\(escapedCallback)';
                var payload={content:'\(escapedResult)',result:'\(escapedResult)',success:true};
                var target=window[callbackName];
                if(typeof target==='function'){
                    target(payload);
                    return;
                }
                var path=callbackName.split('.');
                var scope=window;
                for(var i=0;i<path.length;i++){
                    scope=scope && scope[path[i]];
                }
                if(typeof scope==='function'){
                    scope(payload);
                }
            }catch(e){
                console.error('[WaterValve iOS] '+e.message);
            }
        })();
        """
    }

    static func parseScriptMessage(_ message: Any) -> [String: Any] {
        if let object = message as? [String: Any] {
            return object
        }

        if let text = message as? String,
           let data = text.data(using: .utf8),
           let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            return object
        }

        return [:]
    }

    static func stringValue(_ value: Any?) -> String {
        switch value {
        case let string as String:
            return string
        case let number as NSNumber:
            if floor(number.doubleValue) == number.doubleValue {
                return String(Int64(number.doubleValue))
            }
            return number.stringValue
        default:
            return ""
        }
    }

    private static func escape(_ string: String) -> String {
        string
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "'", with: "\\'")
            .replacingOccurrences(of: "\n", with: "\\n")
            .replacingOccurrences(of: "\r", with: "\\r")
    }
}
