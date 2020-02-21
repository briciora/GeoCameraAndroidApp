package com.example.geocamera;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;
import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, GoogleMap.OnMarkerClickListener, View.OnClickListener {

    //member variables
    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    static final int REQUEST_CAPTURE_IMAGE = 100;
    String imageFilePath;
    private int locationRequestCode = 1000;
    String filePath;

    //used for back button
    private void restartActivity() {
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    //actions for when the back button is clicked
    @Override
    public void onClick(View v) {
        restartActivity();
    }

    //google client connected
    @Override
    public void onConnected(Bundle bundle) {
        Log.d("myTag", "Connected");
    }

    //google client connection suspended
    @Override
    public void onConnectionSuspended(int i) {
        Log.d("myTag", "Suspended");
    }

    //google client no connection
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d("myTag", "Failed");
    }

    //connect the google client
    @Override
    protected void onStart() {
        super.onStart();
        // Connect the client.
        mGoogleApiClient.connect();
    }

    @Override
    public boolean onMarkerClick(Marker marker)
    {
        Double latitude = marker.getPosition().latitude;
        Double longitude = marker.getPosition().longitude;

        //query row of this marker
        String[] projection = {
                "_ID AS " + MyContentProvider.LOCATION_TABLE_COL_ID,
                MyContentProvider.LOCATION_TABLE_COL_LATITUDE,
                MyContentProvider.LOCATION_TABLE_COL_LONGITUDE,
                MyContentProvider.LOCATION_TABLE_COL_IMG};
        final Cursor locationCursor = getContentResolver().query(MyContentProvider.CONTENT_URI, projection,
                "LATITUDE = " + latitude + " AND LONGITUDE = " + longitude, null, null);
        locationCursor.moveToFirst();

        //get path
        String photoFilePath = locationCursor.getString(3);

        //create file with path
        File imgFile = new  File(photoFilePath);
        if(imgFile.exists()){
            Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
            setContentView(R.layout.view_photo);
            ImageView myImage = findViewById(R.id.photoView);
            findViewById(R.id.back).setOnClickListener(this);
            myImage.setImageBitmap(myBitmap);
        }

        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //check permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    locationRequestCode);

        } else {
            // already permission granted
        }

        //create client
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        //launch camera when click new photo
        findViewById(R.id.newPhoto).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCameraIntent();
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMyLocationEnabled(true);
        mMap.setOnMarkerClickListener(this);

        //query all rows and put in cursor
        String[] projection = {
                "_ID AS " + MyContentProvider.LOCATION_TABLE_COL_ID,
                MyContentProvider.LOCATION_TABLE_COL_LATITUDE,
                MyContentProvider.LOCATION_TABLE_COL_LONGITUDE};
        final Cursor locationCursor = getContentResolver().query(MyContentProvider.CONTENT_URI, projection,
                null, null, null);

        //loop through rows, and add markers
        for (locationCursor.moveToFirst(); !locationCursor.isAfterLast(); locationCursor.moveToNext()) {
            LatLng currentRow = new LatLng(locationCursor.getDouble(1), locationCursor.getDouble(2));
            mMap.addMarker(new MarkerOptions().position(currentRow));
            mMap.moveCamera(CameraUpdateFactory.newLatLng(currentRow));
        }
    }

    //create the image file with a date and time file path
    private File createImageFile() throws IOException{

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "IMG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        imageFilePath = image.getAbsolutePath();

        return image;
    }

    //creates an intent to launch the camera
    private void openCameraIntent() {
        Intent pictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (pictureIntent.resolveActivity(getPackageManager()) != null) {

            File photoFile = null;
            try {
                photoFile = createImageFile();
                filePath = photoFile.getAbsolutePath();
            }
            catch (IOException e) {
                e.printStackTrace();
                return;
            }
            String packname = getPackageName() + ".fileprovider";
            Uri photoUri = FileProvider.getUriForFile(this, packname, photoFile);
            pictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            startActivityForResult(pictureIntent, REQUEST_CAPTURE_IMAGE);
        }
    }

    //tracks location after the camera intent has returned the photo
    public void locationPhoto()
    {
        Location location = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);

        if(location != null)
        {
            //Create a ContentValues object
            ContentValues myCV = new ContentValues();
            //Put key_value pairs based on the column names, and the values
            myCV.put(MyContentProvider.LOCATION_TABLE_COL_LATITUDE, location.getLatitude());
            myCV.put(MyContentProvider.LOCATION_TABLE_COL_LONGITUDE, location.getLongitude());
            myCV.put(MyContentProvider.LOCATION_TABLE_COL_IMG, filePath);
            //insert location in the db
            getContentResolver().insert(MyContentProvider.CONTENT_URI, myCV);
            onMapReady(mMap);
        }
    }

    //call locationPhoto() after the intent has been returned
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CAPTURE_IMAGE) {
            if (resultCode == RESULT_OK) {
                locationPhoto();
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "You cancelled the operation", Toast.LENGTH_SHORT).show();
            }
        }
    }
}