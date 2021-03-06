package com.microblink.blinkid;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.widget.Toast;

import com.microblink.BaseMenuActivity;
import com.microblink.MenuListItem;
import com.microblink.activity.DocumentScanActivity;
import com.microblink.entities.recognizers.Recognizer;
import com.microblink.entities.recognizers.RecognizerBundle;
import com.microblink.entities.recognizers.blinkid.mrtd.MrtdRecognizer;
import com.microblink.entities.recognizers.blinkid.mrtd.MrzResult;
import com.microblink.entities.recognizers.detector.DetectorRecognizer;
import com.microblink.entities.recognizers.successframe.SuccessFrameGrabberRecognizer;
import com.microblink.image.Image;
import com.microblink.results.date.Date;
import com.microblink.util.RecognizerCompatibility;
import com.microblink.util.RecognizerCompatibilityStatus;
import com.microblink.util.templating.CroatianIDBackSideTemplatingUtil;
import com.microblink.util.templating.CroatianIDFrontSideTemplatingUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends BaseMenuActivity {

    private static final int SCAN_FRONT_REQ_CODE = 234;
    private static final int SCAN_BACK_REQ_CODE = 432;

    /**
     * Prepares all parsers and templating recognizer for scanning front side of
     * the Croatian ID front side and holds parsers references which are used later for
     * obtaining scan results.
     */
    private CroatianIDFrontSideTemplatingUtil mFrontSideTemplatingUtil;
    private CroatianIDBackSideTemplatingUtil mBackSideTemplatingUtil;

    private DetectorRecognizer mFrontSideTemplatingRecognizer;
    private MrtdRecognizer mBackSideTemplatingRecognizer;

    private SuccessFrameGrabberRecognizer mSuccessFrameGrabberRecognizer;

    private RecognizerBundle mRecognizerBundle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFrontSideTemplatingUtil = new CroatianIDFrontSideTemplatingUtil();
        mBackSideTemplatingUtil = new CroatianIDBackSideTemplatingUtil();

        // check if BlinkInput is supported on the device
        RecognizerCompatibilityStatus supportStatus = RecognizerCompatibility.getRecognizerCompatibilityStatus(this);
        if (supportStatus != RecognizerCompatibilityStatus.RECOGNIZER_SUPPORTED) {
            Toast.makeText(this, "BlinkInput is not supported! Reason: " + supportStatus.name(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected List<MenuListItem> createMenuListItems() {
        List<MenuListItem> items = new ArrayList<>();

        items.add(new MenuListItem(getString(R.string.scan_croatian_id_front), new Runnable() {
            @Override
            public void run() {
                mFrontSideTemplatingRecognizer = mFrontSideTemplatingUtil.getDetectorRecognizer();
                //wrapping into SuccessFrameGrabberRecognizer because we want to show successful scan image
                mSuccessFrameGrabberRecognizer = new SuccessFrameGrabberRecognizer(mFrontSideTemplatingRecognizer);
                startScanActivity(SCAN_FRONT_REQ_CODE);
            }
        }));

        items.add(new MenuListItem(getString(R.string.scan_croatian_id_back), new Runnable() {
            @Override
            public void run() {
                mBackSideTemplatingRecognizer = mBackSideTemplatingUtil.getMRTDRecognizer();
                mSuccessFrameGrabberRecognizer = new SuccessFrameGrabberRecognizer(mBackSideTemplatingRecognizer);
                startScanActivity(SCAN_BACK_REQ_CODE);
            }
        }));

        return items;
    }

    @Override
    protected String getTitleText() {
        return getString(R.string.app_name);
    }

    private void startScanActivity(int requestCode) {
        mRecognizerBundle = new RecognizerBundle(mSuccessFrameGrabberRecognizer);
        mRecognizerBundle.setNumMsBeforeTimeout(10_000);

        Intent intent = new Intent(MainActivity.this, DocumentScanActivity.class);
        mRecognizerBundle.saveToIntent(intent);
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != DocumentScanActivity.RESULT_OK || data == null) {
            return;
        }

        if (requestCode == SCAN_FRONT_REQ_CODE) {
            showFrontScanResults(data);
        } else if (requestCode == SCAN_BACK_REQ_CODE) {
            showBackScanResults(data);
        }
    }

    private void showFrontScanResults(Intent data) {
        // now we can load bundle with scan results, after loading, results will be available
        // through recognizer instances
        mRecognizerBundle.loadFromIntent(data);

        String successFramePath = storeImageToFile(mSuccessFrameGrabberRecognizer.getResult().getSuccessFrame(),
                "successFrame.jpg");
        String fullDocumentPath = storeImageToFile(mFrontSideTemplatingUtil.getFullDocumentImage().getResult().getRawImage(),
                "fullDocument.jpg");
        String facePath = storeImageToFile(mFrontSideTemplatingUtil.getFaceImage().getResult().getRawImage(),
                "face.jpg");

        String resultText = extractFrontSideData();
        if (mFrontSideTemplatingRecognizer.getResult().getResultState() == Recognizer.Result.State.Valid) {
            startActivity(ResultsActivity.buildIntent(this, resultText, successFramePath, fullDocumentPath, facePath));
        }
    }

    private void showBackScanResults(Intent data) {
        mRecognizerBundle.loadFromIntent(data);

        String successFramePath = storeImageToFile(mSuccessFrameGrabberRecognizer.getResult().getSuccessFrame(),
                "successFrame.jpg");
        String fullDocumentPath = storeImageToFile(mBackSideTemplatingUtil.getMRTDRecognizer().getResult().getFullDocumentImage(),
                "fullDocument.jpg");

        String resultText = extractBackSideData();
        if (mBackSideTemplatingRecognizer.getResult().getResultState() == Recognizer.Result.State.Valid) {
            startActivity(ResultsActivity.buildIntent(this, resultText, successFramePath, fullDocumentPath, ""));
        }
    }

    //returns absolute file path
    private String storeImageToFile(Image image, String filename) {
        String filePath;

        try {
            Bitmap bitmap = image.convertToBitmap();
            File imageFile = new File(getFilesDir(), filename);
            OutputStream os = new BufferedOutputStream(new FileOutputStream(imageFile));
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
            filePath = imageFile.getAbsolutePath();
            os.close();
        } catch (Exception e) {
            filePath = null;
        }

        return filePath;
    }

    private String extractFrontSideData() {
        StringBuilder sb = new StringBuilder();
        addEntry(sb, R.string.result_key_first_name,
                mFrontSideTemplatingUtil.getFirstNameParser().getResult().toString());

        addEntry(sb, R.string.result_key_last_name,
                mFrontSideTemplatingUtil.getLastNameParser().getResult().toString());

        // either new or old document number parser contains valid result (depends on the scanned document type)
        int documentNumberKeyResourceId = R.string.result_key_old_document_number;
        String documentNumber = mFrontSideTemplatingUtil.getOldDocumentNumberParser().getResult().toString();
        if (documentNumber.isEmpty()) {
            documentNumber = mFrontSideTemplatingUtil.getNewDocumentNumberParser().getResult().toString();
            documentNumberKeyResourceId = R.string.result_key_new_document_number;
        }
        addEntry(sb, documentNumberKeyResourceId, documentNumber);

        addEntry(sb, R.string.result_key_sex,
                mFrontSideTemplatingUtil.getSexParser().getResult().toString());

        addEntry(sb, R.string.result_key_citizenship,
                mFrontSideTemplatingUtil.getCitizenshipParser().getResult().toString());

        Date dateOfBirth = mFrontSideTemplatingUtil.getDateOfBirthParser().getResult().getDate().getDate();
        if (dateOfBirth != null) {
            addEntry(sb, R.string.result_key_date_of_birth, formatDate(dateOfBirth));
        }

        return sb.toString();
    }

    private String extractBackSideData() {
        StringBuilder sb = new StringBuilder();

        addEntry(sb, R.string.PPAddress, mBackSideTemplatingUtil.getAddressParser().getResult().toString());

        Date issueDate = mBackSideTemplatingUtil.getDateOfIssueParser().getResult().getDate().getDate();
        if (issueDate != null) {
            addEntry(sb, R.string.PPIssueDate, formatDate(issueDate));
        }
        addEntry(sb, R.string.PPIssuedBy, mBackSideTemplatingUtil.getIssuedByParser().getResult().toString());

        //extract everything else from mrz result
        MrzResult mrzResult = mBackSideTemplatingRecognizer.getResult().getMrzResult();
        addEntry(sb, R.string.PPPrimaryId, mrzResult.getPrimaryId());
        addEntry(sb, R.string.PPSecondaryId, mrzResult.getSecondaryId());
        addEntry(sb, R.string.PPSex, mrzResult.getGender());
        addEntry(sb, R.string.PPNationality, mrzResult.getNationality());

        return sb.toString();
    }

    private void addEntry(StringBuilder stringBuilder, @StringRes int entryKeyResourceId, @NonNull String value) {
        stringBuilder.append(getString(entryKeyResourceId)).append(": ").append(value).append("\n");
    }

    private String formatDate(@NonNull Date date) {
        return String.format(Locale.US, "%02d.%02d.%d.", date.getDay(), date.getMonth(), date.getYear());
    }

}
