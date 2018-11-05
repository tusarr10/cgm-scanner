package de.welthungerhilfe.cgm.scanner.helper.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.novoda.merlin.Merlin;
import com.novoda.merlin.registerable.connection.Connectable;
import com.novoda.merlin.registerable.disconnection.Disconnectable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.welthungerhilfe.cgm.scanner.AppController;
import de.welthungerhilfe.cgm.scanner.R;
import de.welthungerhilfe.cgm.scanner.helper.AppConstants;
import de.welthungerhilfe.cgm.scanner.models.FileLog;
import de.welthungerhilfe.cgm.scanner.models.tasks.OfflineTask;
import de.welthungerhilfe.cgm.scanner.utils.Utils;

public class FileLogMonitorService extends Service {
    private List<String> pendingArtefacts;

    private Timer timer = new Timer();
    private ExecutorService executor;

    public void onCreate() {
        pendingArtefacts = new ArrayList<>();
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkFileLogDatabase();
            }
        }, 0, AppConstants.LOG_MONITOR_INTERVAL);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        timer.cancel();
        if (executor != null) {
            executor.shutdownNow();
            pendingArtefacts = new ArrayList<>();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void checkFileLogDatabase() {
        new OfflineTask().getSyncableFileLog(new OfflineTask.OnLoadFileLogs() {
            @Override
            public void onLoadFileLogs(List<FileLog> logs) {
                if (Utils.isNetworkConnectionAvailable(FileLogMonitorService.this)) {
                    if (logs.size() > 0) {
                        executor = Executors.newFixedThreadPool(5);

                        for (FileLog log : logs) {
                            Runnable worker = new UploadThread(log);
                            executor.execute(worker);
                        }
                        executor.shutdown();
                    }
                }
            }
        });
    }

    private class UploadThread implements Runnable {
        FileLog log;

        UploadThread (FileLog log) {
            this.log = log;
        }

        @Override
        public void run() {
            if (pendingArtefacts.contains(log.getId())) {
                return;
            }
            pendingArtefacts.add(log.getId());

            Log.e("pending uploads", pendingArtefacts.toString());

            File file = new File(log.getPath());
            if (!file.exists()) {
                log.setUploadDate(Utils.getUniversalTimestamp());
                log.setDeleted(true);
                new OfflineTask().saveFileLog(log);

                pendingArtefacts.remove(log.getId());
            } else {
                Uri fileUri = Uri.fromFile(file);

                String path = "";
                if (log.getType().equals("pcd"))
                    path = AppConstants.STORAGE_PC_URL.replace("{qrcode}",  log.getQrCode()).replace("{scantimestamp}", String.valueOf(log.getCreateDate()));
                else if (log.getType().equals("rgb"))
                    path = AppConstants.STORAGE_RGB_URL.replace("{qrcode}",  log.getQrCode()).replace("{scantimestamp}", String.valueOf(log.getCreateDate()));
                else if (log.getType().equals("consent"))
                    path = AppConstants.STORAGE_CONSENT_URL.replace("{qrcode}",  log.getQrCode()).replace("{scantimestamp}", String.valueOf(log.getCreateDate()));


                if (path.contains("{qrcode}") || path.contains("scantimestamp")) {
                    Log.e("MonitorService : ", String.format("id: %s, qrcode: %s, scantimestamp: %s", log.getId(), log.getQrCode(), String.valueOf(log.getCreateDate())));
                }

                StorageReference photoRef = FirebaseStorage.getInstance().getReference().child(path)
                        .child(fileUri.getLastPathSegment());

                photoRef.putFile(fileUri)
                        .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                            @Override
                            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                                StorageMetadata metadata = taskSnapshot.getMetadata();
                                if (metadata.getMd5Hash().trim().compareTo(log.getHashValue().trim()) == 0) {
                                    log.setUploadDate(Utils.getUniversalTimestamp());
                                    File file = new File(log.getPath());
                                    if (file.exists() && !log.getType().equals("consent")) {
                                        file.delete();
                                        log.setDeleted(true);
                                    }
                                    log.setPath(photoRef.getDownloadUrl().toString());
                                    new OfflineTask().saveFileLog(log);
                                    AppController.getInstance().firebaseFirestore.collection("artefacts")
                                            .document(log.getId())
                                            .set(log);

                                    pendingArtefacts.remove(log.getId());
                                } else {

                                }
                                pendingArtefacts.remove(log.getId());
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                e.printStackTrace();
                                pendingArtefacts.remove(log.getId());
                            }
                        });
            }
        }
    }
}
