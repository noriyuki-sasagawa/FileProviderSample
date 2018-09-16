package net.newstyleservice.fileprovidersample;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    /** リクエストコード:画像選択 */
    private static final int REQUEST_CODE_PICKER = 1;
    /** リクエストコード:パーミッションチェック */
    private static final int REQUEST_CODE_PERMISSION = 2;

    /** 取得/リサイズした画像表示用ImageView */
    private ImageView mainContent;
    /** 取得/セピアした画像表示用ImageView */
    private ImageView sabContent;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton floatingActionButton = findViewById(R.id.fab);
        floatingActionButton.setOnClickListener(fabOnClickListener);

        mainContent = findViewById(R.id.iv_main_content);
        sabContent = findViewById(R.id.iv_sub_content);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_license) {
            startActivity(new Intent(this, OssLicensesMenuActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_CODE_PICKER && resultCode == RESULT_OK && data != null) {
            Bitmap resizeBitmap = resizeBitmap(data.getData());
            if (resizeBitmap != null) {
                Uri uri = saveImage(resizeBitmap, "resize");
                setMainContent(uri);
            }
            Bitmap bitmap = processedSepia(createBitmap(data.getData()));
            if (bitmap != null) {
                Uri uri = saveImage(bitmap, "sepia");
                setSubContent(uri);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSION) {
            // requestPermissionsで設定した順番で結果が格納されています。
            if (grantResults.length > 0) {
                boolean allowed = true;
                for (int grantResult : grantResults) {
                    if (grantResult == PackageManager.PERMISSION_DENIED) {
                        allowed = false;
                    }
                }
                if (allowed) {
                    // 許可されたので処理を続行
                    showGallery();
                    return;
                }
            }
            // パーミッションのリクエストに対して「許可しない」
            // または以前のリクエストで「二度と表示しない」にチェックを入れられた状態で
            // 「許可しない」を押されていると、必ずここに呼び出されます。

            Toast.makeText(this, "パーミッションが許可されていません。", Toast.LENGTH_SHORT).show();

            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /** ボタンクリックリスナー */
    private View.OnClickListener fabOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (hasPermission()) {
                //パーミッション許可済みor6系未満はそのまま写真選択へ
                showGallery();
            }
        }

        /**
         * パーミッション確認
         * ストレージされたファイルは要READ_EXTERNAL_STORAGE/後に書き込みも行うためWRITE_EXTERNAL_STORAGEも
         * @return true:許可済み false:未許可
         */
        private boolean hasPermission() {
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                // 許可されていない
                if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                        Manifest.permission.READ_EXTERNAL_STORAGE)
                        || ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

                    // すでに１度パーミッションのリクエストが行われていて、
                    // ユーザーに「許可しない（二度と表示しないは非チェック）」をされていると
                    // この処理が呼ばれます。

                    Toast.makeText(MainActivity.this, "パーミッションがOFFになっています。", Toast.LENGTH_SHORT).show();
                } else {
                    // パーミッションのリクエストを表示
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            REQUEST_CODE_PERMISSION);
                }
                return false;
            }
            return true;
        }
    };


    /** ギャラリーアプリなどを立ち上げて選ばれた画像を取得 */
    private void showGallery() {
        Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
        photoPickerIntent.setType("image/*");
        startActivityForResult(photoPickerIntent, REQUEST_CODE_PICKER);
    }

    /**
     * 取得した画像をリサイズ
     *
     * @param uri 画像データ
     * @return リサイズされた画像データ
     */
    private Bitmap resizeBitmap(Uri uri) {
        if (uri != null) {
            try {
                BitmapResizer bitmapResizer = new BitmapResizer(MainActivity.this);
                return bitmapResizer.resize(uri, 200, 200);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
        return null;
    }

    /**
     * URIから画像静止
     *
     * @param uri URIデータ
     * @return 画像
     */
    private Bitmap createBitmap(Uri uri) {
        if (uri != null) {

            try {
                return MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * 画像保存
     *
     * @param bitmap
     * @return
     */
    private Uri saveImage(Bitmap bitmap, String prefixFileName) {

        try {
            String filePath = Environment.getExternalStorageDirectory().getPath()
                    + "/DCIM/sample/" + prefixFileName + "/" + System.currentTimeMillis() + "_img.png";

            File file = new File(filePath);
            file.getParentFile().mkdir();
            FileOutputStream output = new FileOutputStream(filePath);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            output.flush();
            output.close();
            return FileProvider.getUriForFile(
                    MainActivity.this,
                    BuildConfig.APPLICATION_ID + ".fileprovider",
                    file
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * セピア加工
     *
     * @param originalImg 元画像
     * @return セピア加工した画像
     */
    private Bitmap processedSepia(Bitmap originalImg) {
        int width = originalImg.getWidth();
        int height = originalImg.getHeight();
        int pixels[] = new int[width * height];
        originalImg.getPixels(pixels, 0, width, 0, 0, width, height); //(1)
        for (int i = 0; i < pixels.length; i++) {
            //(2)
            int red = (pixels[i] & 0x00FF0000) >> 16;
            int green = (pixels[i] & 0x0000FF00) >> 8;
            int blue = (pixels[i] & 0x000000FF);
            //(3)
            int gray = (77 * red + 150 * green + 29 * blue) >> 8;
            red = green = blue = Math.min(255, Math.round(gray));
            red *= 1;
            green = (int) Math.abs(green * 0.9);
            blue = (int) Math.abs(blue * 0.7);
            pixels[i] = Color.rgb(red, green, blue);
        }
        return Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.RGB_565);
    }

    /**
     * 画像を設定
     *
     * @param uri uri
     */
    private void setMainContent(Uri uri) {
        Picasso.get().load(uri).into(mainContent);
    }

    /**
     * 画像を設定
     *
     * @param uri uri
     */
    private void setSubContent(Uri uri) {
        Picasso.get().load(uri).into(sabContent);
    }
}
