package chessbet.domain;

import java.util.ArrayList;
import java.util.List;

import chessbet.utils.DatabaseUtil;

public class RemoteMove {
    private static  RemoteMove INSTANCE = new RemoteMove();
    private int to;
    private String owner;
    private int from;
    private String pgn;
    private long gameTimeLeft;
    private String promotedPiece;
    private List<String> events;

    private RemoteMove(){
        events = new ArrayList<>();
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public void setFrom(int from) {
        this.from = from;
    }

    public void setTo(int to) {
        this.to = to;
    }

    public void setEvents(ArrayList<String> events) {
        this.events = events;
    }

    public List<String> getEvents() {
        return events;
    }

    public void addEvent(MatchEvent matchEvent){
        if(!this.getEvents().contains(matchEvent.toString())){
            this.events.add(matchEvent.toString());
        }
    }

    public void setPromotedPiece(String promotedPiece) {
        this.promotedPiece = promotedPiece;
    }

    public String getPromotedPiece() {
        return promotedPiece;
    }

    public boolean isLastEventDisconnected(){
        if(events.isEmpty()){
            return false;
        } else {
            int index  = events.size() - 1;
            return events.get(index).equals(MatchEvent.DISCONNECTED.toString());
        }
    }

    public void setGameTimeLeft(long gameTimeLeft) {
        this.gameTimeLeft = gameTimeLeft;
    }

    public long getGameTimeLeft() {
        return gameTimeLeft;
    }

    public String getOwner() {
        return owner;
    }

    public void setPgn(String pgn) {
        this.pgn = pgn;
    }

    public String getPgn() {
        return pgn;
    }

    public int getFrom() {
        return from;
    }

    public int getTo() {
        return to;
    }
    // TODO Change self to enum BLACK || WHITE
    public void send(String matchId, String self){
        DatabaseUtil.sendRemoteMove(matchId,self)
                .setValue(this).addOnSuccessListener(aVoid -> {
        });
    }

    public static RemoteMove get(){
        return INSTANCE;
    }

    public void clear() {
        this.events.clear();
        this.to = 0;
        this.from = 0;
        this.pgn = "";
    }
}
