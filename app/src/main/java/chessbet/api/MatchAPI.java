package chessbet.api;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import chessbet.domain.MatchableAccount;
import chessbet.domain.RemoteMove;
import chessbet.services.MatchListener;
import chessbet.services.RemoteMoveListener;
import chessbet.utils.DatabaseUtil;

public class MatchAPI {
    private FirebaseUser firebaseUser;
    private DatabaseReference  databaseReference;
    private MatchListener matchListener;
    private RemoteMoveListener remoteMoveListener;
    public MatchAPI(){
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
        firebaseUser = firebaseAuth.getCurrentUser();
        databaseReference = FirebaseDatabase.getInstance().getReference();
    }

    public void setMatchListener(MatchListener matchListener) {
        this.matchListener = matchListener;
    }

    public void setRemoteMoveListener(RemoteMoveListener remoteMoveListener) {
        this.remoteMoveListener = remoteMoveListener;
    }

    public void getAccount(){
        final MatchableAccount[] matchable = {null};
        databaseReference.child(DatabaseUtil.matchables).child(firebaseUser.getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                matchable[0] = dataSnapshot.getValue(MatchableAccount.class);
                MatchableAccount matchableAccount = matchable[0];
                if(matchableAccount != null){
                    if(matchableAccount.isMatched()){
                        matchListener.onMatch(matchableAccount);
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }
    public void sendMoveData(MatchableAccount matchableAccount,int source, int destination){
        RemoteMove remoteMove = new RemoteMove();
        remoteMove.setOwner(matchableAccount.getOwner());
        remoteMove.setFrom(source);
        remoteMove.setTo(destination);
        databaseReference.child(DatabaseUtil.matches).child(matchableAccount.getMatchId())
                .child("players")
                .child(matchableAccount.getSelf())
                .setValue(remoteMove).addOnSuccessListener(aVoid -> {

                });
    }

    public void getRemoteMoveData(MatchableAccount matchableAccount){
        databaseReference.child(DatabaseUtil.matches).child(matchableAccount.getMatchId())
                .child("players")
                .child(matchableAccount.getOpponent())
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        RemoteMove remoteMove = dataSnapshot.getValue(RemoteMove.class);
                        if(remoteMove.getFrom() !=0 && remoteMove.getTo()!=0){
                            remoteMoveListener.onRemoteMoveMade(remoteMove);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
    }
}
