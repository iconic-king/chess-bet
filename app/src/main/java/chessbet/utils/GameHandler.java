package chessbet.utils;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.crashlytics.android.Crashlytics;

import java.util.Timer;
import java.util.TimerTask;

import chessbet.api.AccountAPI;
import chessbet.api.MatchAPI;
import chessbet.domain.Constants;
import chessbet.domain.Match;
import chessbet.domain.MatchResult;
import chessbet.domain.MatchStatus;
import chessbet.domain.MatchableAccount;
import chessbet.services.OpponentListener;

public class GameHandler extends AsyncTask<Integer,Void,Void> {
    private static GameHandler INSTANCE = new GameHandler();

    private NoMoveReactor noMoveReactor; // Stores no move reactor components

    public void setNoMoveReactor(NoMoveReactor noMoveReactor) {
        this.noMoveReactor = noMoveReactor;
    }

    public NoMoveReactor getNoMoveReactor() {
        return noMoveReactor;
    }

    private MatchResult matchResult;

    public static final int GAME_INTERRUPTED_FLAG = 18305;
    public static final int GAME_FINISHED_FLAG = 40934;
    public static final int GAME_DRAWN_FLAG = 13780;
    public static final int GAME_TIMER_LAPSED = 472489;
    private static final int GAME_ABANDONED_FLAG = 40428;
    public static final int GAME_ABORTED_FLAG = 10077;
//    private static final int DISCONNECTED_FLAG = 47924;

    public void setMatchResult(MatchResult matchResult) {
        this.matchResult = matchResult;
    }

    public static GameHandler getInstance() {
        return INSTANCE;
    }

    /**
     * Resets Instance Object
     */
    public void resetInstance(){
        INSTANCE = new GameHandler();
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected Void doInBackground(Integer... flags) {
        MatchAPI.get().evaluateMatch(matchResult);
        matchResult = null;
        return null;
    }


    /**
     * To be used in the background to collect data opponent for reference
     */
    public static class BackgroundMatchBuilder extends AsyncTask<Context,Void,Void> {
        private MatchableAccount matchableAccount;
        private OpponentListener opponentListener;

        public void setMatchableAccount(MatchableAccount matchableAccount) {
            this.matchableAccount = matchableAccount;
        }

        @Override
        protected Void doInBackground(Context... contexts) {
            Log.d("OpId", matchableAccount.getOpponentId());
            AccountAPI.get().getAUser(matchableAccount.getOpponentId(), user -> {
                if(user != null){
                   SQLDatabaseHelper sqlDatabaseHelper = new SQLDatabaseHelper(contexts[0]);
                   sqlDatabaseHelper.addMatch(matchableAccount.getMatchId(), user.getProfile_photo_url(), user.getUser_name(), matchableAccount.getMatch_type());
                   Match match = new Match(user.getUser_name(), user.getProfile_photo_url(), matchableAccount.getMatchId(), matchableAccount.getMatch_type());
                   MatchAPI.get().setMatch(match);
                    opponentListener.onOpponentReceived(user);
                }
            });
            return null;
        }

        @Override
        protected void onPreExecute() {
            Log.d(BackgroundMatchBuilder.class.getSimpleName(), "START");
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            Log.d(BackgroundMatchBuilder.class.getSimpleName(), "DONE");
        }

        public void setOpponentListener(OpponentListener opponentListener) {
            this.opponentListener = opponentListener;
        }
    }

    /**
     * Listen to remote moves being made if none within 30 seconds terminate match
     */
    public static class NoMoveReactor  extends AsyncTask<Void,Void,Void>{
        private volatile boolean hasOpponentMoved = false;
        private volatile boolean hasMadeMove;
        private volatile boolean isOpponentDisconnected = false;
        private volatile boolean isMyPly;
        private volatile int noMoveSeconds = 0; // Provide a counter for seconds without move that is changed by any thread
        private volatile boolean isDisconnected = false;
        private MatchableAccount matchableAccount;
        private NoMoveEndMatch noMoveEndMatch;
        private Timer timer;
        private String pgn = "";

        public NoMoveReactor(MatchableAccount matchableAccount){
           this.matchableAccount = matchableAccount;
           timer = new Timer();
        }

        public void setNoMoveEndMatch(NoMoveEndMatch noMoveEndMatch) {
            this.noMoveEndMatch = noMoveEndMatch;
        }

        public void setPgn(String pgn) {
            this.pgn = pgn;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        noMoveSeconds++;
                        if(noMoveSeconds == Constants.TIME_NO_MOVE_END_MATCH && isDisconnected){
                            noMoveEndMatch.onDisconnected(MatchStatus.DISCONNECTED);
                        }
                        else if(noMoveSeconds == Constants.TIME_NO_MOVE_END_MATCH && !isMyPly){
                            if(hasOpponentMoved){
                                matchableAccount.endMatch(pgn, GameHandler.GAME_ABANDONED_FLAG,MatchStatus.ABANDONMENT, matchableAccount.getOwner(), matchableAccount.getOpponentId());
                                noMoveEndMatch.onNoMoveEndMatch(MatchStatus.ABANDONMENT);
                            } else {
                                matchableAccount.endMatch(pgn, GameHandler.GAME_ABORTED_FLAG ,MatchStatus.GAME_ABORTED, "", "");
                                noMoveEndMatch.onNoMoveEndMatch(MatchStatus.GAME_ABORTED);
                            }
                        } else if (noMoveSeconds == Constants.TIME_NO_MOVE_CAUTION && !isMyPly){
                            noMoveEndMatch.onNoMoveOpponentReacting();
                        } else if (noMoveSeconds == Constants.TIME_NO_MOVE_CAUTION && isMyPly){
                            noMoveEndMatch.onNoMoveSelfReacting();
                        } else if (noMoveSeconds == Constants.TIME_NO_MOVE_END_MATCH && isMyPly && !isOpponentDisconnected){
                            if(hasMadeMove){
                                noMoveEndMatch.onLoseNoMove(MatchStatus.ABANDONMENT);
                            } else {
                                noMoveEndMatch.onLoseNoMove(MatchStatus.GAME_ABORTED);
                            }
                        }
                    }catch (Exception ex){
                        Crashlytics.logException(ex);
                    }
                }
            },0,1000);
            return null;
        }

        public void setHasOpponentMoved(boolean hasOpponentMoved) {
            this.hasOpponentMoved = hasOpponentMoved;
        }

        public boolean hasOpponentMoved() {
            return hasOpponentMoved;
        }

        public void clearNoMoveSeconds(){
            this.noMoveSeconds = 0;
        }

        public void stopTimer(){
            timer.cancel();
        }

        public void setMyPly(boolean myPly) {
            isMyPly = myPly;
        }

        public void setHasMadeMove(boolean hasMadeMove) {
            this.hasMadeMove = hasMadeMove;
        }

        public void setDisconnected(boolean disconnected) {
            isDisconnected = disconnected;
        }

        public void setOpponentDisconnected(boolean opponentDisconnected) {
            isOpponentDisconnected = opponentDisconnected;
        }

        public interface NoMoveEndMatch{
            void onNoMoveEndMatch(MatchStatus matchStatus);
            void onNoMoveOpponentReacting();
            void onNoMoveSelfReacting();
            void onLoseNoMove(MatchStatus matchStatus);
            void onDisconnected(MatchStatus matchStatus);
        }
    }
}