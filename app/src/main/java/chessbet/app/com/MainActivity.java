package chessbet.app.com;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import chessbet.api.AccountAPI;
import chessbet.app.com.fragments.GamesFragment;
import chessbet.app.com.fragments.MainFragment;
import chessbet.app.com.fragments.MatchFragment;
import chessbet.app.com.fragments.SettingsFragment;
import chessbet.domain.Account;
import chessbet.domain.User;
import chessbet.services.AccountListener;
import de.hdodenhof.circleimageview.CircleImageView;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, AccountListener, View.OnClickListener {
    private ProgressDialog uploadProfileDialog;
    private StorageReference storageReference;
    private FirebaseStorage firebaseStorage = FirebaseStorage.getInstance();
    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.drawerLayout)
    DrawerLayout drawerLayout;
    @BindView(R.id.nav_viewer) NavigationView navigationView;
    private TextView txtEmail;
    private TextView txtRating;
    private CircleImageView profileImage;
    private static final int REQUEST_CODE = 1001;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        uploadProfileDialog = new ProgressDialog(this);
        uploadProfileDialog.setMessage(getResources().getString(R.string.upload_profile_photo));
        uploadProfileDialog.setCancelable(false);

        ButterKnife.bind(this);
        setSupportActionBar(toolbar);
        txtEmail = navigationView.getHeaderView(0).findViewById(R.id.email);
        txtRating = navigationView.getHeaderView(0).findViewById(R.id.rating);
        profileImage = navigationView.getHeaderView(0).findViewById(R.id.profile_photo);
        profileImage.setOnClickListener(this);
        AccountAPI.get().setUser(FirebaseAuth.getInstance().getCurrentUser());
        AccountAPI.get().getAccount();
        AccountAPI.get().getUser();
        AccountAPI.get().setAccountListener(this);
        navigationView.setNavigationItemSelectedListener(this);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,R.string.open,R.string.close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new MainFragment())
                .addToBackStack(null)
                .commit();
        navigationView.setCheckedItem(R.id.itm_play_chess);
    }

    @Override
    public  void onBackPressed(){
        if(drawerLayout.isDrawerOpen(GravityCompat.START)){
            drawerLayout.closeDrawer( GravityCompat.START);
        }
        else{
            super.onBackPressed();
        }
    }

    @Override
    protected void onStart(){
        super.onStart();
    }

    @Override
    protected void onStop(){
        super.onStop();
    }


    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.itm_play_chess :
                toolbar.setTitle(getString(R.string.app_name));
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new MainFragment()).commit();
                break;
            case R.id.itm_play_online:
                if(AccountAPI.get().getCurrentAccount() != null){
                    toolbar.setTitle(getString(R.string.play_online));
                    getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new MatchFragment()).commit();
                }
            case R.id.itm_profile:
                break;
            case R.id.itm_account_settings:
                toolbar.setTitle(getString(R.string.settings));
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new SettingsFragment()).commit();

                break;
            case R.id.itm_terms:
                Toast.makeText(this, "Accept Terms", Toast.LENGTH_LONG).show();
                break;
            case R.id.games:
                toolbar.setTitle(getString(R.string.games));
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new GamesFragment()).commit();
                break;
        }
        return true;
    }

    @Override
    public void onAccountReceived(Account account) {

        txtRating.setText(getResources().getString(R.string.rating, account.getElo_rating()));
    }

    @Override
    public void onUserReceived(User user) {
        if(user.getProfile_photo_url() != null){
            Glide.with(this).asBitmap().load(user.getProfile_photo_url()).into(profileImage);
        }
        txtEmail.setText(user.getEmail());
    }

    @Override
    public void onClick(View v) {
        if(v.equals(profileImage)){
           getPermissions();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            if(resultCode == RESULT_OK){
                if(requestCode == REQUEST_CODE && data != null){
                    Uri selectedImageUri = data.getData();
                    Glide.with(this).asBitmap().load(selectedImageUri)
                            .listener(new RequestListener<Bitmap>() {
                                @Override
                                public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                                    return false;
                                }

                                @Override
                                public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                                    uploadProfilePhoto(resource);
                                    return false;
                                }
                            })
                            .into(profileImage);
                }
            }
        }catch (Exception e){
            Log.e("FILE ERROR",e.getMessage());
        }
    }

    private void getPermissions(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_CODE);
        }
        else {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, getResources().getString(R.string.select_profile_photo)), REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                startActivityForResult(Intent.createChooser(intent, getResources().getString(R.string.select_profile_photo)), REQUEST_CODE);
            }
        }
    }


    private UploadTask uploadProfilePhotoTask(Bitmap bitmap){
        byte[] bytes = {};
        try {
            uploadProfileDialog.show(); // Shows Dialog
            profileImage.setDrawingCacheEnabled(true);
            profileImage.buildDrawingCache();
//            Bitmap bitmap = ((BitmapDrawable) profileImage.getDrawable()).getBitmap();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG,100,byteArrayOutputStream);
            bytes = byteArrayOutputStream.toByteArray();
        }
        catch (Exception ex){
            uploadProfileDialog.dismiss();
            ex.printStackTrace();
        }
        return storageReference.putBytes(bytes);
    }

    private void uploadProfilePhoto(Bitmap bitmap){
        storageReference = firebaseStorage.getReference(FirebaseAuth.getInstance().getUid() + "/" + "profile_photo");
        uploadProfilePhotoTask(bitmap).addOnFailureListener(e -> {
            uploadProfileDialog.dismiss();
            Log.d("Upload",e.getMessage());
        });

        uploadProfilePhotoTask(bitmap).addOnSuccessListener(taskSnapshot -> storageReference.getDownloadUrl().addOnCompleteListener(task -> {
            final Uri uri = task.getResult();
            Map<String,Object> map = new HashMap<>();
            assert uri != null;
            map.put("profile_photo_url", uri.toString());
            AccountAPI.get().getUserPath().update(map).addOnCompleteListener(task2 -> {
                Toast.makeText(MainActivity.this,R.string.upload_profile_photo,Toast.LENGTH_LONG).show();
                Log.d("Done","Is Done");
                uploadProfileDialog.dismiss();
                AccountAPI.get().getUser();
            }).addOnCanceledListener(() -> uploadProfileDialog.dismiss());
        }));
    }
}

