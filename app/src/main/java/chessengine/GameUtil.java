package chessengine;

import android.content.Context;
import android.media.MediaPlayer;
import android.util.Log;

import java.util.Objects;

public class GameUtil {
    private static MediaPlayer mp;

    public static void  initialize(int resource, Context context){{
        try{
            mp = MediaPlayer.create(context, resource);
        } catch (Exception ex) {
            Log.e("MEDIA_PLAYER", Objects.requireNonNull(ex.getMessage()));
        }
    }

    }
    public static void playSound(){
        try{
            mp.start();
        }
        catch (Exception ex){
            if(ex.getMessage() != null){
                Log.e("MEDIA_PLAYER", ex.getMessage());
            } else {
                ex.printStackTrace();
            }
        }
    }

    public static MediaPlayer getMediaPlayer() {
        return mp;
    }
}
