/*
 * Child Growth Monitor - quick and accurate data on malnutrition
 * Copyright (c) 2018 Markus Matiaschek <mmatiaschek@gmail.com> for Welthungerhilfe
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package de.welthungerhilfe.cgm.scanner.ui.dialogs;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.widget.AppCompatCheckBox;
import android.util.Log;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Collections;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.welthungerhilfe.cgm.scanner.R;
import de.welthungerhilfe.cgm.scanner.datasource.models.Measure;
import de.welthungerhilfe.cgm.scanner.datasource.repository.ArtifactResultRepository;
import de.welthungerhilfe.cgm.scanner.datasource.repository.MeasureResultRepository;
import de.welthungerhilfe.cgm.scanner.helper.AppConstants;
import de.welthungerhilfe.cgm.scanner.ui.views.PrecisionView;
import de.welthungerhilfe.cgm.scanner.utils.Utils;

/**
 * Created by Emerald on 2/23/2018.
 */

public class FeedbackDialog extends Dialog {
    private final int REQUEST_LOCATION = 0x1000;

    @BindView(R.id.txtOverallScore)
    TextView txtOverallScore;
    @BindView(R.id.txtScoreStep1)
    TextView txtScoreStep1;
    @BindView(R.id.txtScoreStep2)
    TextView txtScoreStep2;
    @BindView(R.id.txtScoreStep3)
    TextView txtScoreStep3;
    @BindView(R.id.txtFrontFeedback)
    TextView txtFrontFeedback;
    @BindView(R.id.txtSideFeedback)
    TextView txtSideFeedback;
    @BindView(R.id.txtBackFeedback)
    TextView txtBackFeedback;


    @OnClick(R.id.btnOK)
    void OnConfirm() {
        dismiss();
    }

    private Measure measure;
    private ArtifactResultRepository artifactResultRepository;
    private MeasureResultRepository measureResultRepository;

    public FeedbackDialog(@NonNull Context context) {
        super(context);

        artifactResultRepository = ArtifactResultRepository.getInstance(context);
        measureResultRepository = MeasureResultRepository.getInstance(context);

        this.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.setContentView(R.layout.dialog_measure_feedback);
        this.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        this.getWindow().getAttributes().windowAnimations = R.style.DialogAnimationScale;
        this.setCancelable(false);

        ButterKnife.bind(this);
    }

    public void setMeasure(Measure measure) {
        this.measure = measure;

        updateUI();
    }

    @SuppressLint("StaticFieldLeak")
    private void updateUI() {
        new AsyncTask<Void, Void, Boolean>() {
            private double averagePointCountFront = 0;
            private int pointCloudCountFront = 0;

            private double averagePointCountSide = 0;
            private int pointCloudCountSide = 0;

            private double averagePointCountBack = 0;
            private int pointCloudCountBack = 0;

            private float frontHeightConfidence, sideHeightConfidence, backHeightConfidence;

            @Override
            protected Boolean doInBackground(Void... voids) {
                averagePointCountFront = artifactResultRepository.getAveragePointCountForFront(measure.getId());
                pointCloudCountFront = artifactResultRepository.getPointCloudCountForFront(measure.getId());

                averagePointCountSide = artifactResultRepository.getAveragePointCountForSide(measure.getId());
                pointCloudCountSide = artifactResultRepository.getPointCloudCountForSide(measure.getId());

                averagePointCountBack = artifactResultRepository.getAveragePointCountForBack(measure.getId());
                pointCloudCountBack = artifactResultRepository.getPointCloudCountForBack(measure.getId());

                frontHeightConfidence = measureResultRepository.getConfidence(measure.getId(), "height_front");
                sideHeightConfidence = measureResultRepository.getConfidence(measure.getId(), "height_360");
                backHeightConfidence = measureResultRepository.getConfidence(measure.getId(), "height_back");

                return true;
            }

            @SuppressLint("DefaultLocale")
            public void onPostExecute(Boolean result) {

                double lightScoreFront = (Math.abs(averagePointCountFront / 38000 - 1.0) * 3);
                double durationScoreFront = Math.abs(1- Math.abs((double) pointCloudCountFront / 8 - 1));
                if (lightScoreFront > 1) lightScoreFront -= 1;
                if (durationScoreFront > 1) durationScoreFront -= 1;

                double scoreFront = lightScoreFront * durationScoreFront;
                txtScoreStep1.setText(String.format("%d%%", Math.round(scoreFront * 100)));


                double lightScoreSide = (Math.abs(averagePointCountSide / 38000 - 1.0) * 3);
                double durationScoreSide = Math.abs(1- Math.abs((double) pointCloudCountSide / 24 - 1));
                if (lightScoreSide > 1) lightScoreSide -= 1;
                if (durationScoreSide > 1) durationScoreSide -= 1;

                double scoreSide = lightScoreSide * durationScoreSide;
                txtScoreStep2.setText(String.format("%d%%", Math.round(scoreSide * 100)));


                double lightScoreBack = (Math.abs(averagePointCountBack / 38000 - 1.0) * 3);
                double durationScoreBack = Math.abs(1- Math.abs((double) pointCloudCountBack / 8 - 1));
                if (lightScoreBack > 1) lightScoreBack -= 1;
                if (durationScoreBack > 1) durationScoreBack -= 1;

                double scoreBack = lightScoreBack * durationScoreBack;
                txtScoreStep3.setText(String.format("%d%%", Math.round(scoreBack * 100)));

                double overallScore = 0;
                if (scoreFront > overallScore) overallScore = scoreFront;
                if (scoreSide > overallScore) overallScore = scoreSide;
                if (scoreBack > overallScore) overallScore = scoreBack;

                txtOverallScore.setText(String.format("%d%%", Math.round(overallScore * 100)));

                Log.e("front-light : ", String.valueOf(lightScoreFront));
                Log.e("side-light : ", String.valueOf(lightScoreSide));
                Log.e("back-light : ", String.valueOf(lightScoreBack));

                Log.e("front-duration : ", String.valueOf(durationScoreFront));
                Log.e("side-duration : ", String.valueOf(durationScoreSide));
                Log.e("back-duration : ", String.valueOf(durationScoreBack));

                String issuesFront = String.format(" - Light Score : %d%%", Math.round(lightScoreFront * 100));
                issuesFront = String.format("%s\n - Duration score : %d%%", issuesFront, Math.round(durationScoreFront * 100));
                if (pointCloudCountFront < 8) issuesFront = String.format("%s\n - Duration was too short", issuesFront);
                else if (pointCloudCountFront > 9) issuesFront = String.format("%s\n - Duration was too long", issuesFront);
                issuesFront = String.format("%s\n - Scan Precision : %d%%", issuesFront, Math.round(frontHeightConfidence * 100));
                txtFrontFeedback.setText(issuesFront);

                String issuesSide = String.format(" - Light Score : %d%%", Math.round(lightScoreSide * 100));
                issuesSide = String.format("%s\n - Duration score : %d%%", issuesSide, Math.round(durationScoreSide * 100));
                if (pointCloudCountSide < 12) issuesSide = String.format("%s\n - Duration was too short", issuesSide);
                else if (pointCloudCountSide > 27) issuesSide = String.format("%s\n - Duration was too long", issuesSide);
                issuesSide = String.format("%s\n - Scan Precision : %d%%", issuesSide, Math.round(sideHeightConfidence * 100));
                txtSideFeedback.setText(issuesSide);

                String issuesBack = String.format(" - Light Score : %d%%", Math.round(lightScoreBack * 100));
                issuesBack = String.format("%s\n - Duration score : %d%%", issuesBack, Math.round(durationScoreBack * 100));
                if (pointCloudCountBack < 8) issuesBack = String.format("%s\n - Duration was too short", issuesBack);
                else if (pointCloudCountBack > 9) issuesBack = String.format("%s\n - Duration was too long", issuesBack);
                issuesBack = String.format("%s\n - Scan Precision : %d%%", issuesBack, Math.round(backHeightConfidence * 100));
                txtBackFeedback.setText(issuesBack);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
