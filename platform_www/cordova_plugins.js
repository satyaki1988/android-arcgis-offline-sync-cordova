cordova.define('cordova/plugin_list', function(require, exports, module) {
module.exports = [
    {
        "id": "cordova.plugin.arcGIS.arcGISPlugin",
        "file": "plugins/cordova.plugin.arcGIS/www/arcGISPlugin.js",
        "pluginId": "cordova.plugin.arcGIS",
        "clobbers": [
            "cordova.plugins.arcGISPlugin"
        ]
    }
];
module.exports.metadata = 
// TOP OF METADATA
{
    "cordova-plugin-whitelist": "1.3.2",
    "cordova.plugin.arcGIS": "1.0.0"
};
// BOTTOM OF METADATA
});