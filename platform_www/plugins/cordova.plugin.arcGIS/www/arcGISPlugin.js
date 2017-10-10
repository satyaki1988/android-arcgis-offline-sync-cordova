cordova.define("cordova.plugin.arcGIS.arcGISPlugin", function(require, exports, module) {
var exec = require('cordova/exec');

exports.coolMethod = function(arg0, success, error) {
    exec(success, error, "arcGISPlugin", "coolMethod", [arg0]);
};

exports.loadOfflineMap = function(arg0, success, error) {
    exec(success, error, "arcGISPlugin", "loadOfflineMap", [arg0]);
};

});
