package com.mobileapps.bao.stylit;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.annotation.SuppressLint;
import android.app.LoaderManager;
import android.content.AsyncTaskLoader;
import android.content.Loader;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.widget.Toast;


import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import com.mobileapps.bao.stylit.CapturePhotoUtils;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Bao on 4/21/2018.
 */

public class EditingModule extends AppCompatActivity implements LoaderManager.LoaderCallbacks<String> {
    private static final Logger LOGGER = new Logger();
    protected static final boolean SAVE_PREVIEW_BITMAP = false;
    private ResultsView resultsView;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;
    private long lastProcessingTimeMs;
    private static final int INPUT_SIZE = 299;
    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128;
    private static final String INPUT_NAME = "Mul";
    private static final String OUTPUT_NAME = "final_result";
    private static final String MODEL_FILE = "file:///android_asset/tensorflow_inception_graph.pb";
    private static final String LABEL_FILE = "file:///android_asset/imagenet_comp_graph_label_strings.txt";

    private Classifier classifier;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private BorderedText borderedText;
    String myLog = "myLog";
    AlphaAnimation inAnimation;
    AlphaAnimation outAnimation;
    FrameLayout progressBarHolder;
    ImageView imageViewMain;
    LinearLayout imageScroll;
    EditText classification;
    SeekBar styleBar;
    SeekBar exposureBar;
    SeekBar contrastBar;
    String param;
    Button search;
    Button flickrSort;
    private static final int PICK_IMAGE = 100;
    Uri imageUri;
    Bitmap originalImageBitmap;
    Bitmap originalImageBitmapScaled;
    Bitmap currStyle = null;
    Bitmap currStyleProcessedBitmap = null;
    Bitmap currStyleMix = null;
    ProgressDialog dialog;
    public static final int GETJSONLOADER = 25;
    ArrayList<Photo> al = null;
    ArrayList<ImgTarget> styleTargets = new ArrayList<ImgTarget>();

    static {
        System.loadLibrary("native-lib");
        System.loadLibrary("opencv_java3");
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.editmodule);
        progressBarHolder = (FrameLayout) findViewById(R.id.progressBarHolder);
        search = (Button) findViewById(R.id.search);
        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (classification.getText().toString() != "") {
                    param = classification.getText().toString();
                    styleTargets.clear();
                    imageScroll.removeAllViews();
                    getLoaderManager().restartLoader(GETJSONLOADER, null, EditingModule.this);
                }
                if (getCurrentFocus() != null) {
                    InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
                }
            }
        });
        flickrSort = (Button) findViewById(R.id.flickrSort);
        flickrSort.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PopupMenu popup = new PopupMenu(EditingModule.this, flickrSort);
                //Inflating the Popup using xml file
                popup.getMenuInflater().inflate(R.menu.popup_menu, popup.getMenu());

                //registering popup with OnMenuItemClickListener
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        //Toast.makeText(EditingModule.this, "You Clicked : " + item.getTitle(), Toast.LENGTH_SHORT).show();
                        flickrSort.setText(item.getTitle());
                        styleTargets.clear();
                        imageScroll.removeAllViews();
                        getLoaderManager().restartLoader(GETJSONLOADER, null, EditingModule.this);
                        return true;
                    }
                });
                popup.show();//showing popup menu
            }
        });
        classification = (EditText) findViewById(R.id.classification);
        imageScroll = (LinearLayout) findViewById(R.id.linear);
        imageViewMain = (ImageView) findViewById(R.id.imageView);
        imageViewMain.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    imageViewMain.setImageBitmap(originalImageBitmapScaled);
                } else if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    float exposure = exposureBar.getProgress() - 255;
                    float contrast = contrastBar.getProgress() / 100f;
                    imageViewMain.setImageBitmap(changeBitmapContrastBrightness(currStyleMix, contrast, exposure));
                }
                return true;
            }
        });
        exposureBar = (SeekBar) findViewById(R.id.exposureBar);
        exposureBar.setMax(510);
        exposureBar.setProgress(255);
        exposureBar.incrementProgressBy(15);
        //exposureBar.incrementProgressBy(51);
        exposureBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int tapCounter;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                tapCounter++;
                // TODO Auto-generated method stub
                float exposure = progress - 255;
                float contrast = contrastBar.getProgress() / 100f;
                imageViewMain.setImageBitmap(changeBitmapContrastBrightness(currStyleMix, contrast, exposure));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
                if (tapCounter <= 3)
                    exposureBar.setProgress(255);
                tapCounter = 0;
            }
        });
        contrastBar = (SeekBar) findViewById(R.id.contrastBar);
        contrastBar.setMax(200);
        contrastBar.setProgress(100);
        contrastBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int tapCounter = 0;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                tapCounter++;
                // TODO Auto-generated method stub
                float exposure = exposureBar.getProgress() - 255;
                float contrast = progress / 100f;
                imageViewMain.setImageBitmap(changeBitmapContrastBrightness(currStyleMix, contrast, exposure));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
                if (tapCounter <= 3) {
                    contrastBar.setProgress(100);
                }
                tapCounter = 0;
            }
        });
        styleBar = (SeekBar) findViewById(R.id.styleBar);
        styleBar.getProgressDrawable().setColorFilter(getResources().getColor(R.color.colorPrimary), PorterDuff.Mode.MULTIPLY);
        styleBar.setMax(255);
        styleBar.incrementProgressBy(5);
        styleBar.setProgress(0);
        styleBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {

                // TODO Auto-generated method stub
                if (currStyleProcessedBitmap != null) {
                    Bitmap filterPreviewBitmap = getMaxSizedBitmap(originalImageBitmap.copy(Config.RGB_565, true), 1000);
                    float exposure = exposureBar.getProgress() - 255;
                    float contrast = contrastBar.getProgress() / 100f;
                    currStyleMix = mergeBitmap(filterPreviewBitmap, currStyleProcessedBitmap, progress);
                    imageViewMain.setImageBitmap(changeBitmapContrastBrightness(currStyleMix, contrast, exposure));
                }

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub

            }
        });
        openGallery();
    }
    // create an action bar button
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_bar_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    // handle button activities
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.mybutton) {
            new finalizePhoto().execute();
        }
        return super.onOptionsItemSelected(item);
    }

    private void openGallery() {
        Intent gallery = new Intent(Intent.ACTION_GET_CONTENT, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        gallery.setType("image/*");
        startActivityForResult(gallery, PICK_IMAGE);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == PICK_IMAGE) {
            imageUri = data.getData();
            try {
                originalImageBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                final float textSizePx = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());
                borderedText = new BorderedText(textSizePx);
                borderedText.setTypeface(Typeface.MONOSPACE);

                classifier =
                        TensorFlowImageClassifier.create(
                                getAssets(),
                                MODEL_FILE,
                                LABEL_FILE,
                                INPUT_SIZE,
                                IMAGE_MEAN,
                                IMAGE_STD,
                                INPUT_NAME,
                                OUTPUT_NAME);

                int previewWidth = 480;
                int previewHeight = 640;

                int sensorOrientation = 0;
                LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

                LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
                rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
                croppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888);

                frameToCropTransform = ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        INPUT_SIZE, INPUT_SIZE,
                        sensorOrientation, true);

                cropToFrameTransform = new Matrix();
                frameToCropTransform.invert(cropToFrameTransform);

                classifyImage();

            } catch (IOException e) {
                e.printStackTrace();
            }
            imageViewMain.setImageBitmap(getMaxSizedBitmap(originalImageBitmap.copy(Config.RGB_565, true), 1000));
            currStyleProcessedBitmap = getMaxSizedBitmap(originalImageBitmap.copy(Config.RGB_565, true), 1000);
            currStyle = currStyleProcessedBitmap;
            currStyleMix = currStyleProcessedBitmap;
            originalImageBitmapScaled = currStyleProcessedBitmap;

        }

    }

    void classifyImage() {
        //Log.d("size", Integer.toString(previewHeight) + " " + Integer.toString(previewWidth));
        //rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);
        rgbFrameBitmap = originalImageBitmap;
        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        final long startTime = SystemClock.uptimeMillis();
        final List<Classifier.Recognition> results = classifier.recognizeImage(croppedBitmap);
        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
        LOGGER.i("Detect: %s", results);
        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
        if (results != null) {
            if (results.get(0).getConfidence() > .6) {
                classification.setHint(results.get(0).getTitle() + "@" + results.get(0).getConfidence());
                param = results.get(0).getTitle();
            } else {
                classification.setHint("Unable to Classify");
                param = "";
            }
        } else {
            classification.setHint("Unable to Classify");
            param = "";
        }

        //Toast.makeText(MainActivity.this,NetworkUtils.getBaseUrl(param),Toast.LENGTH_LONG).show();
        if (param != "") {
            styleTargets.clear();
            imageScroll.removeAllViews();
            getLoaderManager().restartLoader(GETJSONLOADER, null, EditingModule.this);
        }
        //resultsView.setResults(results);
        //requestRender();
        //readyForNextImage();
    }

    public static Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        //bm.recycle();
        return resizedBitmap;
    }

    public Bitmap getMaxSizedBitmap(Bitmap image, int maxSize) {
        int width = image.getWidth();
        int height = image.getHeight();

        float bitmapRatio = (float) width / (float) height;
        if (bitmapRatio > 0) {
            width = maxSize;
            height = (int) (width / bitmapRatio);
        } else {
            height = maxSize;
            width = (int) (height * bitmapRatio);
        }
        return Bitmap.createScaledBitmap(image, width, height, true);
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArrayList("array", al);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        al = savedInstanceState.getParcelableArrayList("array");

    }

    @SuppressLint("StaticFieldLeak")
    @Override
    public Loader<String> onCreateLoader(int i, Bundle bundle) {
        switch (i) {
            case GETJSONLOADER:
                return new AsyncTaskLoader<String>(this) {
                    @Override
                    public String loadInBackground() {
                        URL url = null;
                        try {
                            url = new URL(NetworkUtils.getBaseUrl(param, flickrSort.getText().toString()));
                            Log.e("URL", url.toString());
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        }
                        String s = null;
                        try {
                            s = NetworkUtils.getTheResponse(url);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        //Log.e("Return",s);
                        return s;
                    }

                    @Override
                    protected void onStartLoading() {
                        forceLoad();
                        super.onStartLoading();
                    }
                };

        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onLoadFinished(Loader<String> loader, String s) {
        //tv.setText(s);
        switch (loader.getId()) {
            case GETJSONLOADER:
                try {
                    if (s != null) {
                        parse(s);
                        InternalStorage.writeObject(this, param, s);
                    } else {
                        s = InternalStorage.readObject(this, param).toString();
                        Toast.makeText(this, "Internal Storage", Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
        }
    }

    @Override
    public void onLoaderReset(Loader<String> loader) {

    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public ArrayList<Photo> parse(String s) throws JSONException, IOException, ClassNotFoundException {
        al = new ArrayList<>();
        JSONObject jsonObject = new JSONObject(s);
        JSONObject photos = jsonObject.getJSONObject("photos");
        final int pages = photos.getInt("pages");
        JSONArray jsonArray = photos.getJSONArray("photo");
        //Toast.makeText(MainActivity.this,s,Toast.LENGTH_LONG).show();
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject image = jsonArray.getJSONObject(i);
            //Toast.makeText(MainActivity.this,image.getString("owner"),Toast.LENGTH_SHORT).show();
            al.add(new Photo(image.getString("id"), image.getString("owner"), image.getString("secret"), image.getString("server"),
                    image.getInt("farm"), image.getString("title"), image.getInt("ispublic"), image.getInt("isfriend"),
                    image.getInt("isfamily")));
        }
        for (int i = 0; i < 100; i++) {
            Photo photo = al.get(i);
            ImgTarget newImage = new ImgTarget();
            Picasso.with(EditingModule.this).load(NetworkUtils.getImageUrl(photo.getId(), photo.getSecret(), photo.getServer(), photo.getFarm())).into(newImage);
            styleTargets.add(newImage);
        }

        return al;
    }
    private class finalizePhoto extends AsyncTask <Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            inAnimation = new AlphaAnimation(0f, 1f);
            inAnimation.setDuration(200);
            progressBarHolder.setAnimation(inAnimation);
            progressBarHolder.setVisibility(View.VISIBLE);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            outAnimation = new AlphaAnimation(1f, 0f);
            outAnimation.setDuration(200);
            progressBarHolder.setAnimation(outAnimation);
            progressBarHolder.setVisibility(View.GONE);
        }

        @Override
        protected Void doInBackground(Void... params) {
            Bitmap processedFinal = finalImageProcessing(originalImageBitmap,currStyle,styleBar.getProgress(),contrastBar.getProgress()/100f, exposureBar.getProgress()-255);
            CapturePhotoUtils.insertImage(getContentResolver(),processedFinal,System.currentTimeMillis() + "_stylit", "");
            return null;
        }
    }
    //http or db request here
    private class Sample extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... strings) {
            URL url = null;
            try {
                url = new URL(NetworkUtils.getBaseUrl(param, flickrSort.getText().toString()));
                Log.e("URL", url.toString());
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            String s1 = null;
            try {
                s1 = NetworkUtils.getTheResponse(url);
            } catch (IOException e) {
                e.printStackTrace();
            }
            //Log.e("Return",s);
            return s1;
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        protected void onPostExecute(String s1) {
            super.onPostExecute(s1);
        }
    }

    final class ImgTarget implements Target {
        Bitmap originalBitmap;

        @Override
        public void onBitmapLoaded(final Bitmap bitmapLocal, Picasso.LoadedFrom loadedFrom) {
            originalBitmap = bitmapLocal;
            ImageView iV = new ImageView(EditingModule.this);
            iV.setClickable(true);
            iV.setImageBitmap(getMaxSizedBitmap(bitmapLocal, 300));
            iV.setPadding(5, 5, 5, 5);

            iV.setLayoutParams(new LinearLayout.LayoutParams(300, 300));
            iV.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iV.setCropToPadding(true);
            iV.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currStyle = originalBitmap;
                    Bitmap filterPreviewBitmap = getMaxSizedBitmap(originalImageBitmap.copy(Config.RGB_565, true), 1000);
                    Mat filterPreviewMat = new Mat();
                    Utils.bitmapToMat(filterPreviewBitmap, filterPreviewMat, true);
                    Bitmap styleBM = getResizedBitmap(originalBitmap.copy(Config.RGB_565, true), filterPreviewBitmap.getWidth(), filterPreviewBitmap.getHeight());
                    Mat styleMat = new Mat();
                    Utils.bitmapToMat(styleBM, styleMat, true);
                    Mat result = new Mat();
                    Bitmap resultBM = filterPreviewBitmap.copy(Config.RGB_565, true);
                    xiaoTransfer(filterPreviewMat.getNativeObjAddr(), styleMat.getNativeObjAddr(), result.getNativeObjAddr());
                    Imgproc.cvtColor(result, result, Imgproc.COLOR_BGR2RGB);
                    Utils.matToBitmap(result, resultBM);
                    currStyleProcessedBitmap = resultBM;
                    currStyleMix = resultBM;
                    float exposure = exposureBar.getProgress() - 255;
                    float contrast = contrastBar.getProgress() / 100f;
                    imageViewMain.setImageBitmap(changeBitmapContrastBrightness(currStyleProcessedBitmap, contrast, exposure));
                    styleBar.setProgress(styleBar.getMax());
                }
            });
            imageScroll.addView(iV);
        }

        @Override
        public void onBitmapFailed(Drawable drawable) {
            Log.v("IMG Downloader", "Bitmap Failed...");
        }

        @Override
        public void onPrepareLoad(Drawable drawable) {
            Log.v("IMG Downloader", "Bitmap Preparing Load...");
        }

    }

    public Bitmap mergeBitmap(Bitmap original, Bitmap styled, int mergeAmount) {
        Bitmap result = Bitmap.createBitmap(original.getWidth(), original.getHeight(), original.getConfig());
        Canvas canvas = new Canvas(result);
        Paint alphaPaint = new Paint();
        alphaPaint.setAlpha(mergeAmount);
        canvas.drawBitmap(original, 0f, 0f, null);
        canvas.drawBitmap(styled, 0f, 0f, alphaPaint);
        return result;
    }

    public static Bitmap changeBitmapContrastBrightness(Bitmap bmp, float contrast, float brightness) {
        ColorMatrix cm = new ColorMatrix(new float[]
                {
                        contrast, 0, 0, 0, brightness,
                        0, contrast, 0, 0, brightness,
                        0, 0, contrast, 0, brightness,
                        0, 0, 0, 1, 0
                });

        Bitmap ret = Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), bmp.getConfig());

        Canvas canvas = new Canvas(ret);

        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(bmp, 0, 0, paint);

        return ret;
    }
    public Bitmap finalImageProcessing(Bitmap original, Bitmap style,int mixProgress, float contrast, float exposure){
        Mat originalMat = new Mat();
        Utils.bitmapToMat(original,originalMat,true);
        style = getResizedBitmap(style, original.getWidth(), original.getHeight());
        Mat styleMat = new Mat();
        Utils.bitmapToMat(style,styleMat,true);
        Mat result = new Mat();
        Bitmap resultBM = original.copy(Config.RGB_565, true);
        xiaoTransfer(originalMat.getNativeObjAddr(), styleMat.getNativeObjAddr(), result.getNativeObjAddr());
        Imgproc.cvtColor(result, result, Imgproc.COLOR_BGR2RGB);
        Utils.matToBitmap(result, resultBM);
        resultBM = mergeBitmap(original, resultBM, mixProgress);
        return changeBitmapContrastBrightness(resultBM, contrast, exposure);
    }
    public void saveImage(Bitmap finalBitmap, String image_name) {

        String root = Environment.getExternalStorageDirectory().toString();
        File myDir = new File(root);
        myDir.mkdirs();
        String fname = "Image-" + image_name + ".jpg";
        File file = new File(myDir, fname);
        if (file.exists()) file.delete();
        Log.i("LOAD", root + fname);
        try {
            FileOutputStream out = new FileOutputStream(file);
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public native void xiaoTransfer(long target, long source, long result);
}

