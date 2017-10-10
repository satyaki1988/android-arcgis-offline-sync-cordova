package cordova.plugin.arcGIS;

import android.app.ProgressDialog;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.esri.arcgisruntime.arcgisservices.IdInfo;
import com.esri.arcgisruntime.concurrent.Job;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Geodatabase;
import com.esri.arcgisruntime.data.GeodatabaseFeatureTable;
import com.esri.arcgisruntime.data.ServiceFeatureTable;
import com.esri.arcgisruntime.data.TileCache;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.SpatialReference;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.layers.ArcGISTiledLayer;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.mapping.view.SpatialReferenceChangedEvent;
import com.esri.arcgisruntime.mapping.view.SpatialReferenceChangedListener;
import com.esri.arcgisruntime.tasks.geodatabase.GenerateGeodatabaseJob;
import com.esri.arcgisruntime.tasks.geodatabase.GenerateGeodatabaseParameters;
import com.esri.arcgisruntime.tasks.geodatabase.GeodatabaseSyncTask;
import com.esri.arcgisruntime.tasks.geodatabase.SyncGeodatabaseJob;
import com.esri.arcgisruntime.tasks.tilecache.ExportTileCacheJob;
import com.esri.arcgisruntime.tasks.tilecache.ExportTileCacheParameters;
import com.esri.arcgisruntime.tasks.tilecache.ExportTileCacheTask;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * This class echoes a string called from JavaScript.
 */
public class arcGISPlugin extends CordovaPlugin {

    //--- arcGIS map UI properties
    private MapView mapView;
    private ArcGISMap map;
    //--- sync job properties
    private ServiceFeatureTable featureTable;
    private GeodatabaseSyncTask syncTask;
    private GenerateGeodatabaseJob generateJob;
    private SyncGeodatabaseJob syncJob;
    private ArcGISTiledLayer tiledLayer;
    private ExportTileCacheJob job;
    private ExportTileCacheTask exportTask;
    private Geodatabase geodatabase;
    /// PROGRESS BAR
    private ProgressDialog  progressDialog;
    private String strMsg;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressDialog = new ProgressDialog(cordova.getActivity());
                progressDialog.setTitle("Downloading........");
                progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                progressDialog.setCancelable(false);
            }
        });


        if (action.equals("coolMethod")) {
            String message = args.getString(0);
            this.coolMethod(message, callbackContext);
            return true;
        }else if (action.equals("loadOfflineMap")) {
            Toast.makeText(cordova.getActivity(),"Offline method called",Toast.LENGTH_LONG).show();
            this.loadOfflineMap(callbackContext);
            return true;
        }
        return false;
    }

    /***
     * This method for online mapping tool info
     * @param message
     * @param callbackContext
     ***/
    private void coolMethod(String message, CallbackContext callbackContext) {
        if (message != null && message.length() > 0) {

            //--- insert MapView to layout
            mapView = new MapView(cordova.getActivity());

            //--- create new Tiled Layer from service url
            tiledLayer = new ArcGISTiledLayer("https://sampleserver6.arcgisonline.com/arcgis/rest/services/World_Street_Map/MapServer");
            //--- set tiled layer as basemap
            Basemap basemap = new Basemap(tiledLayer);
            //--- create a map with the basemap
            map = new ArcGISMap(basemap);
            map.setInitialViewpoint(new Viewpoint(37.7749, -122.4194, 1));

            //--- set the map to be displayed in this view
            mapView.setMap(map);

//            this.addFeatureLayersFromURL("https://sampleserver6.arcgisonline.com/arcgis/rest/services/DamageAssessment/FeatureServer/");
            this.addFeatureLayersFromURL("https://sampleserver6.arcgisonline.com/arcgis/rest/services/Sync/WildfireSync/FeatureServer/");

            final Button btnDownload = new Button(cordova.getActivity());
            btnDownload.setText("Download");
            btnDownload.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //Toast.makeText(cordova.getActivity(), "Download will start", Toast.LENGTH_LONG).show();
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.setMessage("Request to server..");
                            progressDialog.show();
                        }
                    });

                    downloadMapOverlays();
                }
            });

            cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    cordova.getActivity().addContentView(mapView, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.MATCH_PARENT));

                    cordova.getActivity().addContentView(btnDownload, new LinearLayout.LayoutParams(500, 100));
                }
            });

            Toast.makeText(cordova.getActivity(), message, Toast.LENGTH_LONG).show();
            callbackContext.success(message);
        } else {
            Toast.makeText(cordova.getActivity(), "Plugin Error", Toast.LENGTH_LONG).show();
            callbackContext.error("Expected one non-empty string argument.");
        }
    }

    /***
     * This method is used for if there are no internet connection then it will be loaded automatically. Which is called offline mode
     * @param callbackContext
     */
    private void loadOfflineMap(CallbackContext callbackContext) {
        Toast.makeText(cordova.getActivity(), cordova.getActivity().getFilesDir().getAbsolutePath(), Toast.LENGTH_LONG).show();

        //--- get links to cached resources
        String strTpkPath = cordova.getActivity().getFilesDir().getAbsolutePath()+"/tiles.tpk";
        String strGeoDbPath = cordova.getActivity().getFilesDir().getAbsolutePath()+"/layers.geodatabase";

        File sdCard = Environment.getExternalStorageDirectory();
        File dir = new File (sdCard.getAbsolutePath() + "/arcGIS");
        dir.mkdir();
        File file = new File(sdCard.getAbsolutePath() + "/testfile.txt");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //--- create a tiled layer using the tile package
        TileCache tileCache = new TileCache(strTpkPath);
        tiledLayer = new ArcGISTiledLayer(tileCache);

        //--- set tiled layer as basemap
        Basemap basemap = new Basemap(tiledLayer);

        //--- create a map with the basemap
        map = new ArcGISMap(basemap);

        //--- set the map to be displayed in this view
        mapView = new MapView(cordova.getActivity());
        mapView.setMap(map);

        //--- instantiate geodatabase with name
        geodatabase = new Geodatabase(strGeoDbPath);

        //--- load the geodatabase for feature tables
        geodatabase.loadAsync();

        //--- add feature layer from geodatabase to the ArcGISMap
        geodatabase.addDoneLoadingListener(new Runnable() {
            @Override
            public void run() {
                for (GeodatabaseFeatureTable geoDBTable: geodatabase.getGeodatabaseFeatureTables()) {
                    mapView.getMap().getOperationalLayers().add(new FeatureLayer(geoDBTable));
                }
            }
        });

        //--- set initial viewpoint once MapView has spatial reference
        mapView.addSpatialReferenceChangedListener(new SpatialReferenceChangedListener() {
            @Override
            public void spatialReferenceChanged(SpatialReferenceChangedEvent spatialReferenceChangedEvent) {
                //--- set the initial viewpoint
                Point initPnt = new Point(37.7749, -122.4194, SpatialReference.create(3857));
                mapView.setViewpoint(new Viewpoint(initPnt, 35e4));
            }
        });

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                cordova.getActivity().addContentView(mapView, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT));
            }
        });

        callbackContext.success();
    }

    private void addFeatureLayersFromURL(final String url) {
        syncTask = new GeodatabaseSyncTask(url);
        syncTask.loadAsync();
        syncTask.addDoneLoadingListener(new Runnable() {
            @Override
            public void run() {
                for (int count = 0; count < syncTask.getFeatureServiceInfo().getLayerInfos().size(); count++) {
                    IdInfo layerInfo = syncTask.getFeatureServiceInfo().getLayerInfos().get(count);
                    String layerURL = url.concat(String.valueOf(count));
                    ServiceFeatureTable table = new ServiceFeatureTable(layerURL);
                    FeatureLayer layer = new FeatureLayer(table);
                    layer.setName(layerInfo.getName());
                    map.getOperationalLayers().add(layer);
                }
            }
        });
    }

    private Envelope frameToExtent() {
        double xMin = mapView.getVisibleArea().getExtent().getXMin();
        double yMin = mapView.getVisibleArea().getExtent().getYMin();
        double xMax = mapView.getVisibleArea().getExtent().getXMax();
        double yMax = mapView.getVisibleArea().getExtent().getYMax();

        Envelope extent = new Envelope(xMin, yMin, xMax, yMax, SpatialReferences.getWebMercator());

        return extent;
    }



    private void exportMapTiles() {
        //--- Create the export tile cache
        ExportTileCacheTask exportTilesTask = new ExportTileCacheTask("http://sampleserver6.arcgisonline.com/arcgis/rest/services/World_Street_Map/MapServer");

        //--- Define the parameters for the new tile cache - in this case, using the parameter object constructor
        ExportTileCacheParameters exportTilesParameters = new ExportTileCacheParameters();
        exportTilesParameters.getLevelIDs().addAll(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        Envelope extent = frameToExtent();
        exportTilesParameters.setAreaOfInterest(extent);
//        exportTilesParameters.setAreaOfInterest(
//                new Envelope(-1652366.0, 2939253.0, 2537014.0, 8897484.0, SpatialReferences.getWebMercator()));

        //--- Create the export task, passing in parameters, and file path ending in ".tpk"
        String strTpkPath = cordova.getActivity().getFilesDir().getAbsolutePath()+"/tiles.tpk";
        final ExportTileCacheJob exportJob = exportTilesTask.exportTileCacheAsync(exportTilesParameters, strTpkPath);

        //--- Listen for job status updates, and report the most recent message to the user...
        exportJob.addJobChangedListener(new Runnable() {
            @Override
            public void run() {
                List<Job.Message> messages = exportJob.getMessages();
                strMsg = messages.get(messages.size()-1).getMessage();
                //Toast.makeText(cordova.getActivity(), strMsg, Toast.LENGTH_SHORT).show();
                //progressDialog.setMessage(""+strMsg);
                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.setMessage(""+strMsg);
                    }
                });
            }
        });

        //--- Listen for when the job is completed
        exportJob.addJobDoneListener(new Runnable() {
            @Override
            public void run() {
                //--- Check if there was an error
                if (exportJob.getError() != null) {
                    Toast.makeText(cordova.getActivity(), exportJob.getError().getMessage(), Toast.LENGTH_LONG).show();

                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                        }
                    });
                    return;
                }

                if (exportJob.getStatus() == Job.Status.SUCCEEDED) {
                    //--- Get the TileCache resulting from the successfull export job, and use it by adding a layer to a MapView
                    final TileCache exportedTileCache = exportJob.getResult();
                    Toast.makeText(cordova.getActivity(), "Download Successful", Toast.LENGTH_LONG).show();
                    //progressDialog.setMessage("Map tiles download successful");

                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                        }
                    });
                }
            }
        });

        //--- Start the ExportTileCacheJob
        exportJob.start();
    }




    private void downloadMapOverlays() {
        //--- create a new GeodatabaseSyncTask to create a local version of feature service data, passing in the service url
        syncTask = new GeodatabaseSyncTask("https://sampleserver6.arcgisonline.com/arcgis/rest/services/Sync/WildfireSync/FeatureServer");

//        final Envelope extent = new Envelope(-1652366.0, 2939253.0, 2537014.0, 8897484.0, SpatialReferences.getWebMercator());
        final Envelope extent = frameToExtent();
        //--- get the default parameters for generating a geodatabase
        final ListenableFuture<GenerateGeodatabaseParameters> listenableFuture = syncTask.createDefaultGenerateGeodatabaseParametersAsync(extent);
        listenableFuture.addDoneListener(new Runnable() {
            @Override
            public void run() {
                try {
                    //--- get default parameters
                    GenerateGeodatabaseParameters params= listenableFuture.get();

                    //--- changes required to the parameters, attachment sync disabled for minimizing geodb size
                    params.setReturnAttachments(false);

                    generateGeoDatabase(params);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    private void generateGeoDatabase(final GenerateGeodatabaseParameters params) {
        //--- create the generate geodatabase job, pass in the parameters and an output path for the local geodatabase
        String strGeoDbPath = cordova.getActivity().getFilesDir().getAbsolutePath()+"/layers.geodatabase";

        final GenerateGeodatabaseJob generateGeodatabaseJob = syncTask.generateGeodatabaseAsync(params, strGeoDbPath);

        //--- add a job changed listener to check the status of the job
        generateGeodatabaseJob.addJobChangedListener(new Runnable() {
            @Override
            public void run() {
                //--- for example, if the job is still running, report the last message to the user
                final List<Job.Message> messages = generateGeodatabaseJob.getMessages();
                //Toast.makeText(cordova.getActivity(), messages.get(messages.size()-1).getMessage(), Toast.LENGTH_SHORT).show();
                //progressDialog.setMessage(""+messages.get(messages.size()-1).getMessage());
                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.setMessage(""+messages.get(messages.size()-1).getMessage());
                    }
                });
            }
        });

        //--- add a job done listener to deal with job completion - success or failure
        generateGeodatabaseJob.addJobDoneListener(new Runnable() {
            @Override
            public void run() {
                if (generateGeodatabaseJob.getStatus() == Job.Status.FAILED) {
                    //--- deal with job failure - check the error details on the job
                    Toast.makeText(cordova.getActivity(),"Layer Download Failed", Toast.LENGTH_LONG).show();
                    //progressDialog.setMessage("Layer Download Failed");
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                        }
                    });

                    return;
                }
                else if (generateGeodatabaseJob.getStatus() == Job.Status.SUCCEEDED)
                {
                    //--- if the job succeeded, the geodatabase is now available at the given local path.
                    Toast.makeText(cordova.getActivity(),"Layer Download Succeeded", Toast.LENGTH_LONG).show();
                    //progressDialog.setMessage("Layer Download Succeeded");
                    //progressDialog.dismiss();
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.setMessage("Layer Download Succeeded");
                        }
                    });
                    geodatabase = generateGeodatabaseJob.getResult();

                    exportMapTiles();
                }
            }
        });

        //--- start the job to generate and download the geodatabase
        generateGeodatabaseJob.start();
    }

    /*@Override
    public void onDestroy() {
        //super.onDestroy();
    }*/
}
