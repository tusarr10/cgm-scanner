package de.welthungerhilfe.cgm.scanner.ui.activities;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationManager;
import android.media.Image;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.Pose;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.experimental.TangoImageBuffer;
import com.google.gson.Gson;
import com.microsoft.appcenter.crashes.Crashes;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.queue.CloudQueue;
import com.microsoft.azure.storage.queue.CloudQueueClient;
import com.microsoft.azure.storage.queue.CloudQueueMessage;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidKeyException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.welthungerhilfe.cgm.scanner.AppController;
import de.welthungerhilfe.cgm.scanner.R;
import de.welthungerhilfe.cgm.scanner.datasource.database.CgmDatabase;
import de.welthungerhilfe.cgm.scanner.datasource.models.ArtifactList;
import de.welthungerhilfe.cgm.scanner.datasource.models.ArtifactResult;
import de.welthungerhilfe.cgm.scanner.datasource.models.FileLog;
import de.welthungerhilfe.cgm.scanner.datasource.models.Loc;
import de.welthungerhilfe.cgm.scanner.datasource.models.Measure;
import de.welthungerhilfe.cgm.scanner.datasource.models.Person;
import de.welthungerhilfe.cgm.scanner.datasource.models.LocalPersistency;
import de.welthungerhilfe.cgm.scanner.datasource.repository.ArtifactResultRepository;
import de.welthungerhilfe.cgm.scanner.datasource.repository.FileLogRepository;
import de.welthungerhilfe.cgm.scanner.datasource.repository.MeasureRepository;
import de.welthungerhilfe.cgm.scanner.helper.AppConstants;
import de.welthungerhilfe.cgm.scanner.helper.SessionManager;
import de.welthungerhilfe.cgm.scanner.helper.camera.ARCoreCamera;
import de.welthungerhilfe.cgm.scanner.helper.camera.ICamera;
import de.welthungerhilfe.cgm.scanner.helper.camera.TangoCamera;
import de.welthungerhilfe.cgm.scanner.helper.receiver.AddressReceiver;
import de.welthungerhilfe.cgm.scanner.helper.service.AddressService;
import de.welthungerhilfe.cgm.scanner.helper.service.UploadService;
import de.welthungerhilfe.cgm.scanner.utils.ARCoreUtils;
import de.welthungerhilfe.cgm.scanner.utils.BitmapUtils;
import de.welthungerhilfe.cgm.scanner.utils.MD5;
import de.welthungerhilfe.cgm.scanner.utils.TangoUtils;
import de.welthungerhilfe.cgm.scanner.utils.Utils;

import static de.welthungerhilfe.cgm.scanner.helper.AppConstants.SCAN_LYING;
import static de.welthungerhilfe.cgm.scanner.helper.AppConstants.SCAN_LYING_BACK;
import static de.welthungerhilfe.cgm.scanner.helper.AppConstants.SCAN_LYING_FRONT;
import static de.welthungerhilfe.cgm.scanner.helper.AppConstants.SCAN_LYING_SIDE;
import static de.welthungerhilfe.cgm.scanner.helper.AppConstants.SCAN_PREVIEW;
import static de.welthungerhilfe.cgm.scanner.helper.AppConstants.SCAN_STANDING;
import static de.welthungerhilfe.cgm.scanner.helper.AppConstants.SCAN_STANDING_BACK;
import static de.welthungerhilfe.cgm.scanner.helper.AppConstants.SCAN_STANDING_FRONT;
import static de.welthungerhilfe.cgm.scanner.helper.AppConstants.SCAN_STANDING_SIDE;

public class ScanModeActivity extends AppCompatActivity implements View.OnClickListener, ARCoreCamera.Camera2DataListener, TangoCamera.TangoCameraListener {
    private final int PERMISSION_LOCATION = 0x0001;
    private final int PERMISSION_CAMERA = 0x0002;
    private final int PERMISSION_STORAGE = 0x0002;

    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.imgScanStanding)
    ImageView imgScanStanding;
    @BindView(R.id.imgScanStandingCheck)
    ImageView imgScanStandingCheck;
    @BindView(R.id.txtScanStanding)
    TextView txtScanStanding;

    @BindView(R.id.imgScanLying)
    ImageView imgScanLying;
    @BindView(R.id.imgScanLyingCheck)
    ImageView imgScanLyingCheck;
    @BindView(R.id.txtScanLying)
    TextView txtScanLying;

    @BindView(R.id.imgScanStep1)
    ImageView imgScanStep1;
    @BindView(R.id.imgScanStep2)
    ImageView imgScanStep2;
    @BindView(R.id.imgScanStep3)
    ImageView imgScanStep3;

    @BindView(R.id.btnScanStep1)
    Button btnScanStep1;
    @BindView(R.id.btnScanStep2)
    Button btnScanStep2;
    @BindView(R.id.btnScanStep3)
    Button btnScanStep3;
    @BindView(R.id.btnScanComplete)
    Button btnScanComplete;

    @BindView(R.id.lytScanStep1)
    LinearLayout lytScanStep1;
    @BindView(R.id.lytScanStep2)
    LinearLayout lytScanStep2;
    @BindView(R.id.lytScanStep3)
    LinearLayout lytScanStep3;

    @BindView(R.id.lytSelectMode)
    LinearLayout lytSelectMode;

    @BindView(R.id.lytScanSteps)
    LinearLayout lytScanSteps;
    @BindView(R.id.lytScanner)
    LinearLayout lytScanner;

    @BindView(R.id.imgScanSuccess1)
    ImageView imgScanSuccess1;
    @BindView(R.id.imgScanSuccess2)
    ImageView imgScanSuccess2;
    @BindView(R.id.imgScanSuccess3)
    ImageView imgScanSuccess3;

    @BindView(R.id.txtScanStep1)
    TextView txtScanStep1;
    @BindView(R.id.txtScanStep2)
    TextView txtScanStep2;
    @BindView(R.id.txtScanStep3)
    TextView txtScanStep3;

    @BindView(R.id.lytScanAgain1)
    LinearLayout lytScanAgain1;
    @BindView(R.id.lytScanAgain2)
    LinearLayout lytScanAgain2;
    @BindView(R.id.lytScanAgain3)
    LinearLayout lytScanAgain3;

    @BindView(R.id.btnRetake1)
    Button btnRetake1;
    @BindView(R.id.btnRetake2)
    Button btnRetake2;
    @BindView(R.id.btnRetake3)
    Button btnRetake3;

    @BindView(R.id.btnTutorial1)
    Button btnTutorial1;
    @BindView(R.id.btnTutorial2)
    Button btnTutorial2;
    @BindView(R.id.btnTutorial3)
    Button btnTutorial3;

    @OnClick(R.id.lytScanStanding)
    void scanStanding() {
        SCAN_MODE = SCAN_STANDING;

        imgScanStanding.setImageResource(R.drawable.standing_active);
        imgScanStandingCheck.setImageResource(R.drawable.radio_active);
        txtScanStanding.setTextColor(getResources().getColor(R.color.colorBlack, getTheme()));

        imgScanLying.setImageResource(R.drawable.lying_inactive);
        imgScanLyingCheck.setImageResource(R.drawable.radio_inactive);
        txtScanLying.setTextColor(getResources().getColor(R.color.colorGreyDark, getTheme()));

        changeMode();
    }
    @OnClick(R.id.lytScanLying)
    void scanLying() {
        SCAN_MODE = SCAN_LYING;

        imgScanLying.setImageResource(R.drawable.lying_active);
        imgScanLyingCheck.setImageResource(R.drawable.radio_active);
        txtScanLying.setTextColor(getResources().getColor(R.color.colorBlack, getTheme()));

        imgScanStanding.setImageResource(R.drawable.standing_inactive);
        imgScanStandingCheck.setImageResource(R.drawable.radio_inactive);
        txtScanStanding.setTextColor(getResources().getColor(R.color.colorGreyDark, getTheme()));

        changeMode();
    }
    @SuppressLint("SetTextI18n")
    @OnClick({R.id.btnScanStep1, R.id.btnRetake1})
    void scanStep1() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{"android.permission.CAMERA"}, PERMISSION_CAMERA);
        } else {
            if (SCAN_MODE == SCAN_STANDING) {
                SCAN_STEP = SCAN_STANDING_FRONT;

                mTitleView.setText(getString(R.string.front_view_01) + " - " + getString(R.string.mode_standing));
            } else if (SCAN_MODE == SCAN_LYING) {
                SCAN_STEP = SCAN_LYING_FRONT;

                mTitleView.setText(getString(R.string.front_view_01) + " - " + getString(R.string.mode_lying));
            }

            fab.setImageResource(R.drawable.recorder);
            lytScanner.setVisibility(View.VISIBLE);
        }
    }
    @SuppressLint("SetTextI18n")
    @OnClick({R.id.btnScanStep2, R.id.btnRetake2})
    void scanStep2() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{"android.permission.CAMERA"}, PERMISSION_CAMERA);
        } else {
            if (SCAN_MODE == SCAN_STANDING) {
                SCAN_STEP = SCAN_STANDING_SIDE;

                mTitleView.setText(getString(R.string.lateral_view_02) + " - " + getString(R.string.mode_standing));
            } else if (SCAN_MODE == SCAN_LYING) {
                SCAN_STEP = SCAN_LYING_SIDE;

                mTitleView.setText(getString(R.string.lateral_view_02) + " - " + getString(R.string.mode_lying));
            }

            fab.setImageResource(R.drawable.recorder);
            lytScanner.setVisibility(View.VISIBLE);
        }
    }
    @SuppressLint("SetTextI18n")
    @OnClick({R.id.btnScanStep3, R.id.btnRetake3})
    void scanStep3() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{"android.permission.CAMERA"}, PERMISSION_CAMERA);
        } else {
            if (SCAN_MODE == SCAN_STANDING) {
                SCAN_STEP = SCAN_STANDING_BACK;

                mTitleView.setText(getString(R.string.back_view_03) + " - " + getString(R.string.mode_standing));
            } else if (SCAN_MODE == SCAN_LYING) {
                SCAN_STEP = SCAN_LYING_BACK;

                mTitleView.setText(getString(R.string.back_view_03) + " - " + getString(R.string.mode_lying));
            }

            fab.setImageResource(R.drawable.recorder);
            lytScanner.setVisibility(View.VISIBLE);
        }
    }

    @OnClick({R.id.btnTutorial1, R.id.btnTutorial2, R.id.btnTutorial3})
    void showTutorial() {
        Intent intent = new Intent(ScanModeActivity.this, TutorialActivity.class);
        intent.putExtra(AppConstants.EXTRA_TUTORIAL_AGAIN, true);
        startActivity(intent);
    }

    @OnClick(R.id.btnScanComplete)
    void completeScan() {
        measure.setCreatedBy(session.getUserEmail());
        measure.setDate(Utils.getUniversalTimestamp());
        measure.setAge(age);
        measure.setType(AppConstants.VAL_MEASURE_AUTO);
        measure.setWeight(0.0f);
        measure.setHeight(0.0f);
        measure.setHeadCircumference(0.0f);
        measure.setMuac(0.0f);
        measure.setOedema(false);
        measure.setPersonId(person.getId());
        measure.setTimestamp(Utils.getUniversalTimestamp());
        measure.setQrCode(person.getQrcode());
        measure.setSchema_version(CgmDatabase.version);

        progressDialog.show();

        if (LocalPersistency.getBoolean(this, SettingsPerformanceActivity.KEY_TEST_RESULT)) {
            LocalPersistency.setString(this, SettingsPerformanceActivity.KEY_TEST_RESULT_ID, measure.getId());
            LocalPersistency.setLong(this, SettingsPerformanceActivity.KEY_TEST_RESULT_SCAN, System.currentTimeMillis());
            LocalPersistency.setLong(this, SettingsPerformanceActivity.KEY_TEST_RESULT_START, 0);
            LocalPersistency.setLong(this, SettingsPerformanceActivity.KEY_TEST_RESULT_END, 0);
            LocalPersistency.setLong(this, SettingsPerformanceActivity.KEY_TEST_RESULT_RECEIVE, 0);
        }
        new SaveMeasureTask(ScanModeActivity.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private AddressReceiver receiver = new AddressReceiver(new Handler()) {
        @Override
        public void onAddressDetected(String result) {
            location.setAddress(result);
            measure.setLocation(location);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            super.onReceiveResult(resultCode, resultData);
        }
    };

    private static final String TAG = ScanModeActivity.class.getSimpleName();

    public int SCAN_MODE = SCAN_STANDING;
    public int SCAN_STEP = SCAN_PREVIEW;
    private boolean step1 = false, step2 = false, step3 = false;

    public Person person;
    public Measure measure;
    public Loc location;

    private MeasureRepository measureRepository;
    private FileLogRepository fileLogRepository;
    private ArtifactResultRepository artifactResultRepository;

    private SessionManager session;

    private TextView mTitleView;
    private ProgressBar progressBar;
    private FloatingActionButton fab;

    // variables for Pose and point clouds
    private int mNumberOfFilesWritten;

    private File mScanArtefactsOutputFolder;
    private File mDepthmapSaveFolder;
    private File mPointCloudSaveFolder;
    private File mRgbSaveFolder;

    private boolean mIsRecording;
    private int mProgress;

    private long mNowTime;
    private String mNowTimeString;

    private long mColorSize;
    private long mColorTime;
    private long mDepthSize;
    private long mDepthTime;

    private long age = 0;

    private AlertDialog progressDialog;

    private ExecutorService executor;
    private int threadsCount = 0;
    private final Object threadsLock = new Object();

    private ICamera mCameraInstance;

    public void onStart() {
        super.onStart();

        mNumberOfFilesWritten = 0;
        mIsRecording = false;

        mColorSize = 0;
        mColorTime = 0;
        mDepthSize = 0;
        mDepthTime = 0;
        if (LocalPersistency.getBoolean(this, SettingsPerformanceActivity.KEY_TEST_PERFORMANCE)) {
            LocalPersistency.setLong(this, SettingsPerformanceActivity.KEY_TEST_PERFORMANCE_COLOR_SIZE, 0);
            LocalPersistency.setLong(this, SettingsPerformanceActivity.KEY_TEST_PERFORMANCE_DEPTH_SIZE, 0);
            LocalPersistency.setLong(this, SettingsPerformanceActivity.KEY_TEST_PERFORMANCE_COLOR_TIME, 0);
            LocalPersistency.setLong(this, SettingsPerformanceActivity.KEY_TEST_PERFORMANCE_DEPTH_TIME, 0);
        }
    }

    protected void onCreate(Bundle savedBundle) {
        super.onCreate(savedBundle);

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Crashes.trackError(throwable);
            finish();
        });

        person = (Person) getIntent().getSerializableExtra(AppConstants.EXTRA_PERSON);
        measure = (Measure) getIntent().getSerializableExtra(AppConstants.EXTRA_MEASURE);

        if (person == null) {
            Toast.makeText(this, R.string.person_not_defined, Toast.LENGTH_LONG).show();
            finish();
        }

        executor = Executors.newFixedThreadPool(20);

        mNowTime = System.currentTimeMillis();
        mNowTimeString = String.valueOf(mNowTime);

        session = new SessionManager(this);

        age = (System.currentTimeMillis() - person.getBirthday()) / 1000 / 60 / 60 / 24;

        if (measure == null) {
            measure = new Measure();
            measure.setId(AppController.getInstance().getMeasureId());
            measure.setQrCode(person.getQrcode());
            measure.setCreatedBy(session.getUserEmail());
            measure.setAge(age);
            measure.setDate(System.currentTimeMillis());
            measure.setArtifact_synced(false);
        }

        setContentView(R.layout.activity_scan_mode);

        ButterKnife.bind(this);

        mTitleView = findViewById(R.id.txtTitle);
        progressBar = findViewById(R.id.progressBar);
        fab = findViewById(R.id.fab_scan_result);
        fab.setOnClickListener(this);

        findViewById(R.id.btnRetake).setOnClickListener(this);
        findViewById(R.id.imgClose).setOnClickListener(this);

        getCamera().onCreate();

        measureRepository = MeasureRepository.getInstance(this);
        fileLogRepository = FileLogRepository.getInstance(this);
        artifactResultRepository = ArtifactResultRepository.getInstance(this);

        setupToolbar();

        getCurrentLocation();

        setupScanArtifacts();

        progressDialog = new AlertDialog.Builder(this)
                .setCancelable(false)
                .setView(R.layout.dialog_loading)
                .create();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, PERMISSION_STORAGE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        getCamera().onResume();
        getCamera().addListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getCamera().removeListener(this);
        getCamera().onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        progressDialog.dismiss();
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setTitle(R.string.title_add_measure);
    }

    private void setupScanArtifacts() {
        File extFileDir = AppController.getInstance().getRootDirectory();

        Log.e("Root Directory", extFileDir.getParent());
        mScanArtefactsOutputFolder = new File(extFileDir,person.getQrcode() + "/measurements/" + mNowTimeString + "/");
        mDepthmapSaveFolder = new File(mScanArtefactsOutputFolder,"depth");
        mPointCloudSaveFolder = new File(mScanArtefactsOutputFolder, "pc");
        mRgbSaveFolder = new File(mScanArtefactsOutputFolder,"rgb");

        if(!isTangoDevice() && !mDepthmapSaveFolder.exists()) {
            boolean created = mDepthmapSaveFolder.mkdirs();
            if (created) {
                Log.i(TAG, "Folder: \"" + mDepthmapSaveFolder + "\" created\n");
            } else {
                Log.e(TAG,"Folder: \"" + mDepthmapSaveFolder + "\" could not be created!\n");
            }
        }

        if(!mPointCloudSaveFolder.exists()) {
            boolean created = mPointCloudSaveFolder.mkdirs();
            if (created) {
                Log.i(TAG, "Folder: \"" + mPointCloudSaveFolder + "\" created\n");
            } else {
                Log.e(TAG,"Folder: \"" + mPointCloudSaveFolder + "\" could not be created!\n");
            }
        }

        if(!mRgbSaveFolder.exists()) {
            boolean created = mRgbSaveFolder.mkdirs();
            if (created) {
                Log.i(TAG, "Folder: \"" + mRgbSaveFolder + "\" created\n");
            } else {
                Log.e(TAG,"Folder: \"" + mRgbSaveFolder + "\" could not be created!\n");
            }
        }

        if (!isTangoDevice())
            Log.v(TAG,"mDepthmapSaveFolder: "+mDepthmapSaveFolder);
        Log.v(TAG,"mPointCloudSaveFolder: "+mPointCloudSaveFolder);
        Log.v(TAG,"mRgbSaveFolder: "+mRgbSaveFolder);
    }

    private void updateScanningProgress(int numPoints, float density) {
        float minPointsToCompleteScan = 199500.0f;
        float maxProgressPerframe = Math.min(numPoints, minPointsToCompleteScan * density);
        float progressToAddFloat = maxProgressPerframe / minPointsToCompleteScan;
        progressToAddFloat = progressToAddFloat*100;
        int progressToAdd = (int) progressToAddFloat;
        Log.d(TAG, "numPoints: "+numPoints+" float: "+progressToAddFloat+" currentProgress: "+mProgress+" progressToAdd: "+progressToAdd);
        if (mProgress+progressToAdd > 100) {
            mProgress = 100;
            runOnUiThread(() -> fab.setImageResource(R.drawable.done));
        } else {
            mProgress = mProgress+progressToAdd;
        }

        Log.d("scan_progress", String.valueOf(mProgress));
        Log.d("scan_progress_step", String.valueOf(progressToAdd));
    }

    private void changeMode() {
        if (SCAN_MODE == SCAN_STANDING) {
            imgScanStep1.setImageResource(R.drawable.stand_front_active);
            imgScanStep2.setImageResource(R.drawable.stand_side_active);
            imgScanStep3.setImageResource(R.drawable.stand_back_active);
        } else if (SCAN_MODE == SCAN_LYING) {
            imgScanStep1.setImageResource(R.drawable.lying_front_active);
            imgScanStep2.setImageResource(R.drawable.lying_side_active);
            imgScanStep3.setImageResource(R.drawable.lying_back_active);
        }
    }

    public void goToNextStep() {
        closeScan();

        getScanQuality(measure.getId(),SCAN_STEP);
    }

    private void showCompleteButton() {
        btnScanComplete.setVisibility(View.VISIBLE);
        btnScanComplete.requestFocus();

        int cx = (btnScanComplete.getLeft() + btnScanComplete.getRight()) / 2;
        int cy = (btnScanComplete.getTop() + btnScanComplete.getBottom()) / 2;

        int dx = Math.max(cx, btnScanComplete.getWidth() - cx);
        int dy = Math.max(cy, btnScanComplete.getHeight() - cy);
        float finalRadius = (float) Math.hypot(dx, dy);

        Animator animator = ViewAnimationUtils.createCircularReveal(btnScanComplete, cx, cy, 0, finalRadius);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.setDuration(300);
        animator.start();
    }

    private void hideCompleteButton() {
        int cx = (btnScanComplete.getLeft() + btnScanComplete.getRight()) / 2;
        int cy = (btnScanComplete.getTop() + btnScanComplete.getBottom()) / 2;

        int dx = Math.max(cx, btnScanComplete.getWidth() - cx);
        int dy = Math.max(cy, btnScanComplete.getHeight() - cy);
        float finalRadius = (float) Math.hypot(dx, dy);

        Animator animator = ViewAnimationUtils.createCircularReveal(btnScanComplete, cx, cy, finalRadius, 0);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.setDuration(300);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                btnScanComplete.setVisibility(View.GONE);
            }
        });
        animator.start();
    }

    private void startScan() {
        mProgress = 0;

        resumeScan();
    }

    private void resumeScan() {
        if (SCAN_STEP == SCAN_PREVIEW)
            return;

        mIsRecording = true;
        fab.setImageResource(R.drawable.stop);
    }

    private void pauseScan() {
        mIsRecording = false;
        fab.setImageResource(R.drawable.recorder);
    }

    public void closeScan() {
        mIsRecording = false;
        progressBar.setProgress(0);
        mProgress = 0;

        lytScanner.setVisibility(View.GONE);
    }

    @SuppressLint("StaticFieldLeak")
    private void getScanQuality(String measureId, int scanStep) {
        new AsyncTask<Void, Void, Boolean>() {
            private double averagePointCount = 0;
            private int pointCloudCount = 0;

            @Override
            protected Boolean doInBackground(Void... voids) {
                averagePointCount = artifactResultRepository.getAveragePointCount(measureId, scanStep);
                pointCloudCount = artifactResultRepository.getPointCloudCount(measureId, scanStep);

                return true;
            }

            @SuppressLint("DefaultLocale")
            public void onPostExecute(Boolean results) {
                double lightScore = (Math.abs(averagePointCount / 38000 - 1.0) * 3);

                double durationScore;
                if (scanStep % 100 == 1)
                    durationScore = Math.abs(1- Math.abs((double) pointCloudCount / 24 - 1));
                else
                    durationScore = Math.abs(1- Math.abs((double) pointCloudCount / 8 - 1));

                if (lightScore > 1) lightScore -= 1;
                if (durationScore > 1) durationScore -= 1;

                Log.e("ScanQuality", String.valueOf(lightScore));
                Log.e("DurationQuality", String.valueOf(durationScore));

                String issues = getString(R.string.scan_quality);
                issues = String.format("%s\n - " + getString(R.string.score_light) + "%d%%", issues, Math.round(lightScore * 100));
                issues = String.format("%s\n - " + getString(R.string.score_duration) + "%d%%", issues, Math.round(durationScore * 100));

                if (scanStep == SCAN_STANDING_FRONT || scanStep == SCAN_LYING_FRONT) {
                    btnScanStep1.setVisibility(View.GONE);

                    if (pointCloudCount < 8) {
                        issues = String.format("%s\n - " + getString(R.string.score_duration_short), issues);
                    } else if (pointCloudCount > 9) {
                        issues = String.format("%s\n - " + getString(R.string.score_duration_long), issues);
                    }

                    txtScanStep1.setText(issues);
                    imgScanStep1.setVisibility(View.GONE);
                    lytScanAgain1.setVisibility(View.VISIBLE);

                    step1 = true;

                } else if (scanStep == SCAN_STANDING_SIDE || scanStep == SCAN_LYING_SIDE) {
                    btnScanStep2.setVisibility(View.GONE);

                    if (pointCloudCount < 12) {
                        issues = String.format("%s\n - " + getString(R.string.score_duration_short), issues);
                    } else if (pointCloudCount > 27) {
                        issues = String.format("%s\n - " + getString(R.string.score_duration_long), issues);
                    }
                    txtScanStep2.setText(issues);
                    imgScanStep2.setVisibility(View.GONE);
                    lytScanAgain2.setVisibility(View.VISIBLE);

                    step2 = true;

                } else if (scanStep == SCAN_STANDING_BACK || scanStep == SCAN_LYING_BACK) {
                    btnScanStep3.setVisibility(View.GONE);

                    if (pointCloudCount < 8) {
                        issues = String.format("%s\n - " + getString(R.string.score_duration_short), issues);
                    } else if (pointCloudCount > 9) {
                        issues = String.format("%s\n - " + getString(R.string.score_duration_long), issues);
                    }

                    txtScanStep3.setText(issues);
                    imgScanStep3.setVisibility(View.GONE);
                    lytScanAgain3.setVisibility(View.VISIBLE);

                    step3 = true;
                }

                if (step1 && step2 && step3) {
                    showCompleteButton();
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{"android.permission.ACCESS_FINE_LOCATION"}, PERMISSION_LOCATION);
        } else {
            LocationManager lm = (LocationManager)getApplicationContext().getSystemService(Context.LOCATION_SERVICE);

            boolean isGPSEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean isNetworkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            Location loc = null;

            if (!isGPSEnabled && !isNetworkEnabled) {
                startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            } else {
                List<String> providers = lm.getProviders(true);
                for (String provider : providers) {
                    Location l = lm.getLastKnownLocation(provider);
                    if (l == null) {
                        continue;
                    }
                    if (loc == null || l.getAccuracy() < loc.getAccuracy()) {
                        loc = l;
                    }
                }
                if (loc != null) {
                    location = new Loc();

                    location.setLatitude(loc.getLatitude());
                    location.setLongitude(loc.getLongitude());

                    Intent intent = new Intent(this, AddressService.class);
                    intent.putExtra("add_receiver", receiver);
                    intent.putExtra("add_location", loc);
                    startService(intent);
                }
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_LOCATION && grantResults.length > 0 && grantResults[0] >= 0) {
            getCurrentLocation();
        }
        if (requestCode == PERMISSION_CAMERA && (grantResults.length == 0 || grantResults[0] < 0)) {
            Toast.makeText(ScanModeActivity.this, R.string.permission_camera, Toast.LENGTH_SHORT).show();
            finish();
        }
        if (requestCode == PERMISSION_STORAGE && (grantResults.length == 0 || grantResults[0] < 0)) {
            Toast.makeText(ScanModeActivity.this, R.string.storage_permission_needed, Toast.LENGTH_SHORT).show();
            finish();
        }
        setupScanArtifacts();
    }

    public void onBackPressed() {
        if (lytScanner.getVisibility() == View.VISIBLE) {
            lytScanner.setVisibility(View.GONE);
        } else {
            finish();
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.fab_scan_result:
                if (mIsRecording) {
                    if (mProgress >= 100) {
                        goToNextStep();
                    } else {
                        pauseScan();
                    }
                } else {
                    if (mProgress > 0) {
                        resumeScan();
                    } else {
                        startScan();
                    }
                }
                break;
            case R.id.imgClose:
                closeScan();
                break;
            case R.id.btnRetake:
                mProgress = 0;
                break;
        }
    }

    @SuppressLint("StaticFieldLeak")
    class SaveMeasureTask extends AsyncTask<Void, Void, Void> {
        private Activity activity;

        SaveMeasureTask(Activity act) {
            activity = act;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            Gson gson = new Gson();

            waitUntilFinished();
            measureRepository.insertMeasure(measure);

            try {
                CloudStorageAccount storageAccount = CloudStorageAccount.parse(AppController.getInstance().getAzureConnection());
                CloudQueueClient queueClient = storageAccount.createCloudQueueClient();

                try {
                    if (!measure.isArtifact_synced()) {
                        CloudQueue measureArtifactsQueue = queueClient.getQueueReference("artifact-list");
                        measureArtifactsQueue.createIfNotExists();

                        long totalNumbers  = fileLogRepository.getTotalArtifactCountForMeasure(measure.getId());
                        final int size = 50;
                        int offset = 0;

                        while (offset + 1 < totalNumbers) {
                            List<FileLog> measureArtifacts = fileLogRepository.getArtifactsForMeasure(measure.getId(), offset, size);

                            ArtifactList artifactList = new ArtifactList();
                            artifactList.setMeasure_id(measure.getId());
                            artifactList.setStart(offset + 1);
                            artifactList.setEnd(offset + measureArtifacts.size());
                            artifactList.setArtifacts(measureArtifacts);
                            artifactList.setTotal(totalNumbers);

                            offset += measureArtifacts.size();

                            CloudQueueMessage measureArtifactsMessage = new CloudQueueMessage(measure.getId());
                            measureArtifactsMessage.setMessageContent(gson.toJson(artifactList));
                            measureArtifactsQueue.addMessage(measureArtifactsMessage);
                        }

                        measure.setArtifact_synced(true);
                        measure.setUploaded_at(System.currentTimeMillis());
                    }

                    CloudQueue measureQueue = queueClient.getQueueReference("measure");
                    measureQueue.createIfNotExists();

                    CloudQueueMessage message = new CloudQueueMessage(measure.getId());
                    message.setMessageContent(gson.toJson(measure));
                    measureQueue.addMessage(message);

                    measure.setTimestamp(session.getSyncTimestamp());
                    measureRepository.updateMeasure(measure);
                } catch (StorageException e) {
                    e.printStackTrace();
                }
            } catch (URISyntaxException | InvalidKeyException e) {
                e.printStackTrace();
            }

            return null;
        }

        public void onPostExecute(Void result) {
            if (!AppController.getInstance().isUploadRunning()) {
                startService(new Intent(ScanModeActivity.this, UploadService.class));
            }
            activity.finish();
        }
    }

    private ICamera getCamera() {
        if (mCameraInstance == null) {
            if (isTangoDevice()) {
                mCameraInstance = new TangoCamera(this);
            } else {
                mCameraInstance = new ARCoreCamera(this);
            }
        }
        return mCameraInstance;
    }

    private boolean isTangoDevice() {
        //Note: the compatibility is checked by AndroidManifest
        return Build.VERSION.SDK_INT <= 24;
    }

    @Override
    public void onColorDataReceived(Bitmap bitmap, int frameIndex) {
        if (mIsRecording && (frameIndex % 10 == 0)) {

            long profile = System.currentTimeMillis();
            ARCoreCamera.CameraCalibration calibration = ((ARCoreCamera)mCameraInstance).getCalibration();

            Runnable thread = () -> {

                //write RGB data
                String currentImgFilename = "rgb_" + person.getQrcode() + "_" + mNowTimeString + "_" + SCAN_STEP + "_" + frameIndex + ".jpg";
                currentImgFilename = currentImgFilename.replace('/', '_');
                File artifactFile = new File(mRgbSaveFolder, currentImgFilename);
                try {
                    FileOutputStream fileOutputStream = new FileOutputStream(artifactFile);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, fileOutputStream);
                    fileOutputStream.flush();
                    fileOutputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                //upload RGB data
                if (artifactFile.exists()) {
                    mColorSize += artifactFile.length();
                    mColorTime += System.currentTimeMillis() - profile;
                    if (LocalPersistency.getBoolean(this, SettingsPerformanceActivity.KEY_TEST_PERFORMANCE)) {
                        LocalPersistency.setLong(this, SettingsPerformanceActivity.KEY_TEST_PERFORMANCE_COLOR_SIZE, mColorSize);
                        LocalPersistency.setLong(this, SettingsPerformanceActivity.KEY_TEST_PERFORMANCE_COLOR_TIME, mColorTime);
                    }

                    FileLog log = new FileLog();
                    log.setId(AppController.getInstance().getArtifactId("scan-rgb", mNowTime));
                    log.setType("rgb");
                    log.setPath(artifactFile.getPath());
                    log.setHashValue(MD5.getMD5(artifactFile.getPath()));
                    log.setFileSize(artifactFile.length());
                    log.setUploadDate(0);
                    log.setDeleted(false);
                    log.setQrCode(person.getQrcode());
                    log.setCreateDate(mNowTime);
                    log.setCreatedBy(session.getUserEmail());
                    log.setAge(age);
                    log.setSchema_version(CgmDatabase.version);
                    log.setMeasureId(measure.getId());

                    fileLogRepository.insertFileLog(log);
                }

                //write and upload calibration
                artifactFile = new File(mScanArtefactsOutputFolder, "camera_calibration.txt");
                if (!artifactFile.exists()) {
                    if (calibration.isValid()) {
                        try {
                            FileOutputStream fileOutputStream = new FileOutputStream(artifactFile.getAbsolutePath());
                            fileOutputStream.write(calibration.toString().getBytes());
                            fileOutputStream.flush();
                            fileOutputStream.close();

                            FileLog log = new FileLog();
                            log.setId(AppController.getInstance().getArtifactId("camera-calibration", mNowTime));
                            log.setType("calibration");
                            log.setPath(artifactFile.getPath());
                            log.setHashValue(MD5.getMD5(artifactFile.getPath()));
                            log.setFileSize(artifactFile.length());
                            log.setUploadDate(0);
                            log.setDeleted(false);
                            log.setQrCode(person.getQrcode());
                            log.setCreateDate(mNowTime);
                            log.setCreatedBy(session.getUserEmail());
                            log.setAge(age);
                            log.setSchema_version(CgmDatabase.version);
                            log.setMeasureId(measure.getId());

                            fileLogRepository.insertFileLog(log);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                onThreadChange(-1);
            };
            onThreadChange(1);
            executor.execute(thread);
        }
    }

    @Override
    public void onDepthDataReceived(Image image, Pose pose, int frameIndex) {
        if (mIsRecording && (frameIndex % 10 == 0)) {

            long profile = System.currentTimeMillis();
            ARCoreUtils.Depthmap depthmap = ARCoreUtils.extractDepthmap(image, pose);
            long timestamp = depthmap.getTimestamp();
            float density = ((ARCoreCamera)mCameraInstance).getDepthSensorDensity();
            float lightIntensity = ((ARCoreCamera)mCameraInstance).getLightIntensity() * 2.0f;
            float lightOverbright = Math.min(Math.max(lightIntensity - 1.0f, 0.0f), 0.99f);
            float Artifact_Light_estimation = Math.min(lightIntensity, 0.99f) - lightOverbright;
            double Artifact_Confidence_penalty = Math.abs((double) depthmap.getCount()/38000-1.0)*100*3;
            ARCoreCamera.CameraCalibration calibration = ((ARCoreCamera)mCameraInstance).getCalibration();

            String depthmapFilename = "depth_" + person.getQrcode() + "_" + mNowTimeString + "_" + SCAN_STEP + "_" + frameIndex + ".depth";
            String pointCloudFilename = "pcd_" + person.getQrcode() + "_" + mNowTimeString + "_" + SCAN_STEP + "_" + frameIndex + ".pcd";
            mNumberOfFilesWritten++;

            updateScanningProgress(depthmap.getCount(), density);
            progressBar.setProgress(mProgress);

            int scanStep = SCAN_STEP;
            new Thread(() -> {
                ArtifactResult ar = new ArtifactResult();
                ar.setConfidence_value(String.valueOf(100 - Artifact_Confidence_penalty));
                ar.setArtifact_id(AppController.getInstance().getPersonId());
                ar.setKey(scanStep);
                ar.setMeasure_id(measure.getId());
                ar.setMisc("");
                ar.setType("PCD_POINTS_v0.2");
                ar.setReal(38000 * (1.0f + Artifact_Light_estimation / 3.0f));
                artifactResultRepository.insertArtifactResult(ar);
            }).start();

            Runnable thread = () -> {

                //write depthmap
                File artifactFile = new File(mDepthmapSaveFolder, depthmapFilename);
                ARCoreUtils.writeDepthmap(depthmap, artifactFile);

                //write pointcloud
                File artifactFilePCD = new File(mPointCloudSaveFolder.getPath(), pointCloudFilename);
                ARCoreUtils.writeDepthmapToPcdFile(depthmap, calibration, timestamp, mPointCloudSaveFolder, pointCloudFilename);

                //profile process
                if (artifactFile.exists() && artifactFilePCD.exists()) {
                    mDepthSize += artifactFile.length() + artifactFilePCD.length();
                    mDepthTime += System.currentTimeMillis() - profile;
                    if (LocalPersistency.getBoolean(this, SettingsPerformanceActivity.KEY_TEST_PERFORMANCE)) {
                        LocalPersistency.setLong(this, SettingsPerformanceActivity.KEY_TEST_PERFORMANCE_DEPTH_SIZE, mDepthSize);
                        LocalPersistency.setLong(this, SettingsPerformanceActivity.KEY_TEST_PERFORMANCE_DEPTH_TIME, mDepthTime);
                    }
                }

                //upload depthmap
                if (artifactFile.exists()) {
                    FileLog log = new FileLog();
                    log.setId(AppController.getInstance().getArtifactId("scan-depth", mNowTime));
                    log.setType("depth");
                    log.setPath(artifactFile.getPath());
                    log.setHashValue(MD5.getMD5(artifactFile.getPath()));
                    log.setFileSize(artifactFile.length());
                    log.setUploadDate(0);
                    log.setDeleted(false);
                    log.setQrCode(person.getQrcode());
                    log.setCreateDate(mNowTime);
                    log.setCreatedBy(session.getUserEmail());
                    log.setAge(age);
                    log.setSchema_version(CgmDatabase.version);
                    log.setMeasureId(measure.getId());
                    fileLogRepository.insertFileLog(log);
                }

                //upload pointcloud
                if (artifactFilePCD.exists()) {
                    FileLog log = new FileLog();
                    log.setId(AppController.getInstance().getArtifactId("scan-pcd", mNowTime));
                    log.setType("pcd");
                    log.setPath(artifactFilePCD.getPath());
                    log.setHashValue(MD5.getMD5(artifactFilePCD.getPath()));
                    log.setFileSize(artifactFilePCD.length());
                    log.setUploadDate(0);
                    log.setDeleted(false);
                    log.setQrCode(person.getQrcode());
                    log.setCreateDate(mNowTime);
                    log.setCreatedBy(session.getUserEmail());
                    log.setAge(age);
                    log.setSchema_version(CgmDatabase.version);
                    log.setMeasureId(measure.getId());
                    fileLogRepository.insertFileLog(log);
                }
                onThreadChange(-1);
            };
            onThreadChange(1);
            executor.execute(thread);
        }
    }

    @Override
    public void onTangoColorData(TangoImageBuffer tangoImageBuffer) {
        if ( ! mIsRecording) {
            return;
        }

        TangoImageBuffer currentTangoImageBuffer = TangoUtils.copyImageBuffer(tangoImageBuffer);
        String currentImgFilename = "rgb_" + person.getQrcode() +"_" + mNowTimeString + "_" + SCAN_STEP + "_" + currentTangoImageBuffer.timestamp + ".jpg";

        Runnable thread = () -> {
            long profile = System.currentTimeMillis();
            BitmapUtils.writeImageToFile(currentTangoImageBuffer, mRgbSaveFolder, currentImgFilename);
            File artifactFile = new File(mRgbSaveFolder.getPath() + File.separator + currentImgFilename);

            if (artifactFile.exists()) {
                mColorSize += artifactFile.length();
                mColorTime += System.currentTimeMillis() - profile;
                if (LocalPersistency.getBoolean(this, SettingsPerformanceActivity.KEY_TEST_PERFORMANCE)) {
                    LocalPersistency.setLong(this, SettingsPerformanceActivity.KEY_TEST_PERFORMANCE_COLOR_SIZE, mColorSize);
                    LocalPersistency.setLong(this, SettingsPerformanceActivity.KEY_TEST_PERFORMANCE_COLOR_TIME, mColorTime);
                }

                FileLog log = new FileLog();
                log.setId(AppController.getInstance().getArtifactId("scan-rgb", mNowTime));
                log.setType("rgb");
                log.setPath(artifactFile.getPath());
                log.setHashValue(MD5.getMD5(artifactFile.getPath()));
                log.setFileSize(artifactFile.length());
                log.setUploadDate(0);
                log.setDeleted(false);
                log.setQrCode(person.getQrcode());
                log.setCreateDate(mNowTime);
                log.setCreatedBy(session.getUserEmail());
                log.setAge(age);
                log.setSchema_version(CgmDatabase.version);
                log.setMeasureId(measure.getId());

                fileLogRepository.insertFileLog(log);
            }
            onThreadChange(-1);
        };
        onThreadChange(1);
        executor.execute(thread);
    }

    @Override
    public void onTangoDepthData(TangoPointCloudData pointCloudData, String pose) {
        // Saving the frame or not, depending on the current mode.
        if ( mIsRecording ) {
            long profile = System.currentTimeMillis();
            int numPoints = pointCloudData.numPoints;
            double timestamp = pointCloudData.timestamp;
            ByteBuffer buffer = ByteBuffer.allocate(pointCloudData.numPoints * 4 * 4);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.asFloatBuffer().put(pointCloudData.points);

            updateScanningProgress(numPoints, 1);
            progressBar.setProgress(mProgress);

            String pointCloudFilename = "pcd_" + person.getQrcode() + "_" + mNowTimeString + "_" + SCAN_STEP +
                    "_" + String.format(Locale.getDefault(), "%03d", mNumberOfFilesWritten++);

            int scanStep = SCAN_STEP;
            new Thread(() -> {
                ArtifactResult ar=new ArtifactResult();
                double Artifact_Lighting_penalty=Math.abs((double) numPoints/38000-1.0)*100*3;
                ar.setConfidence_value(String.valueOf(100-Artifact_Lighting_penalty));
                ar.setArtifact_id(AppController.getInstance().getPersonId());
                ar.setKey(scanStep);
                ar.setMeasure_id(measure.getId());
                ar.setMisc("");
                ar.setType("PCD_POINTS_v0.2");
                ar.setReal(numPoints);
                artifactResultRepository.insertArtifactResult(ar);
            }).start();

            Runnable thread = () -> {
                File artifactFile = new File(mPointCloudSaveFolder.getPath() + File.separator + pointCloudFilename +".pcd");
                TangoUtils.writePointCloudToPcdFile(buffer, numPoints, timestamp, pose, artifactFile);

                if (artifactFile.exists()) {
                    mDepthSize += artifactFile.length();
                    mDepthTime += System.currentTimeMillis() - profile;
                    if (LocalPersistency.getBoolean(this, SettingsPerformanceActivity.KEY_TEST_PERFORMANCE)) {
                        LocalPersistency.setLong(this, SettingsPerformanceActivity.KEY_TEST_PERFORMANCE_DEPTH_SIZE, mDepthSize);
                        LocalPersistency.setLong(this, SettingsPerformanceActivity.KEY_TEST_PERFORMANCE_DEPTH_TIME, mDepthTime);
                    }

                    FileLog log = new FileLog();
                    log.setId(AppController.getInstance().getArtifactId("scan-pcd", mNowTime));
                    log.setType("pcd");
                    log.setPath(artifactFile.getPath());
                    log.setHashValue(MD5.getMD5(artifactFile.getPath()));
                    log.setFileSize(artifactFile.length());
                    log.setUploadDate(0);
                    log.setDeleted(false);
                    log.setQrCode(person.getQrcode());
                    log.setCreateDate(mNowTime);
                    log.setCreatedBy(session.getUserEmail());
                    log.setAge(age);
                    log.setSchema_version(CgmDatabase.version);
                    log.setMeasureId(measure.getId());
                    fileLogRepository.insertFileLog(log);

                    Log.e("numbs", String.valueOf(mNumberOfFilesWritten));
                }
                onThreadChange(-1);
            };
            onThreadChange(1);
            executor.execute(thread);
        }
    }

    private void onThreadChange(int diff) {
        synchronized (threadsLock) {
            threadsCount += diff;
            if (threadsCount == 0) {
                Log.d("ScanModeActivity", "The last thread finished");
                threadsLock.notify();
            } else {
                Log.d("ScanModeActivity", "Amount of threads : " + threadsCount);
            }
        }
    }

    private void waitUntilFinished() {
        synchronized (threadsLock) {
            if (threadsCount > 0) {
                Log.d("ScanModeActivity", "Start waiting on running threads");
                try {
                    threadsLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Log.d("ScanModeActivity", "Stop waiting on running threads");
            } else {
                Log.d("ScanModeActivity", "All threads already finished");
            }
        }
    }
}
