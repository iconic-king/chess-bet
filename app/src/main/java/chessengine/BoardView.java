package chessengine;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.graphics.Canvas;

import com.chess.engine.Alliance;
import com.chess.engine.Move;
import com.chess.engine.board.Board;
import com.chess.engine.board.BoardUtils;
import com.chess.engine.board.Tile;
import com.chess.engine.pieces.Piece;
import com.chess.engine.player.MoveTransition;
import com.chess.engine.player.Player;
import com.google.common.collect.Lists;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import chessbet.api.MatchAPI;
import chessbet.domain.MatchableAccount;
import chessbet.domain.RemoteMove;
import chessbet.services.RemoteMoveListener;
import chessbet.services.RemoteViewUpdateListener;

public class BoardView extends View implements RemoteMoveListener, Serializable {
    protected Board chessBoard;
    protected Tile sourceTile;
    protected Tile destinationTile;
    protected Piece movedPiece;
    private List<Cell> boardCells=null;
    private BoardDirection boardDirection;
    protected Alliance topAlliance = Alliance.BLACK;
    private int squareSize = 0;
    private boolean isFlipped = false;
    private int whiteCellsColor;
    private int darkCellsColor;
    protected MoveLog moveLog;
    protected OnMoveDoneListener onMoveDoneListener;
    private MatchableAccount matchableAccount;
    private MatchAPI matchAPI;
    private Alliance localAlliance;
    private RemoteViewUpdateListener remoteViewUpdateListener;
    protected int moveCursor = 0;
    private List<Rect> tiles = new ArrayList<>();

    private void initialize(Context context){
        setSaveEnabled(true);
        moveLog= new MoveLog();
        chessBoard= Board.createStandardBoard();
        boardCells=new ArrayList<>();
        boardDirection = BoardDirection.REVERSE;
        for (int i=0 ; i < BoardUtils.NUMBER_OF_TILES; i++){
            final  Cell cell = new Cell(context,this,i);
            this.boardCells.add(cell);
        }
    }

    public BoardView(Context context, AttributeSet attributeSet){
        super(context,attributeSet);
        initialize(context);
    }

    public  BoardView(Context context){
        super(context);
       initialize(context);
    }

    @Override
    public boolean performClick(){
        super.performClick();
        return true;
    }

    private void createTileRectangles(){
        final int width=getWidth();
        final int height=getHeight();

        squareSize=Math.min(getCellWidth(width),getCellHeight(height));

        for (int c = 0; c < BoardUtils.NUMBER_OF_TILES_PER_ROW; c++) {
            for (int r = 0; r < BoardUtils.NUMBER_OF_TILES_PER_ROW; r++) {
                final int xCoord = getXCoord(r);
                final int yCoord = getYCoord(c);

                final Rect tileRect = new Rect(
                        xCoord,
                        yCoord,
                        xCoord + squareSize,
                        yCoord + squareSize
                );
                tiles.add(tileRect);
            }
        }
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        performClick();
        if(event.getAction() == MotionEvent.ACTION_DOWN){
            final int x = (int) event.getX();
            final int y = (int) event.getY();
            for (int i = 0; i < BoardUtils.NUMBER_OF_TILES; i++) {
                if(boardCells.get(i).isTouched(x,y)){
                        if(matchableAccount != null  && chessBoard.getTile(i).isOccupied()){
                            if(chessBoard.getTile(i).getPiece().getPieceAlliance() == localAlliance){
                                boardCells.get(i).handleTouch();
                            }
                            else if(chessBoard.getTile(i).getPiece().getPieceAlliance() != localAlliance && movedPiece!=null){
                                // Allows user to take a piece in an online game
                                boardCells.get(i).handleTouch();
                            }
                        }
                        else if(matchableAccount !=null && !chessBoard.getTile(i).isOccupied()){
                            boardCells.get(i).handleTouch();
                        }
                        else {
                            boardCells.get(i).handleTouch();
                        }
                }
            }
        }
        return true;
    }


    private int measureDimension(int desiredSize, int measuredSpec){
        int result;
        int specMode = MeasureSpec.getMode(measuredSpec);
        int specSize = MeasureSpec.getSize(measuredSpec);

        if(specMode  == MeasureSpec.EXACTLY){
            result = specSize;
        }
        else {
            result = desiredSize;
            if(specMode == MeasureSpec.AT_MOST){
                result = Math.min(result,specSize);
            }
        }

        if(result < desiredSize){
            Log.e("Board View","Too Small A Size");
        }

        return result;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec){
        if(this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT){
            int desiredWidth = getSuggestedMinimumWidth() + getPaddingLeft() + getPaddingRight();
            setMeasuredDimension(measureDimension(desiredWidth,widthMeasureSpec), measureDimension(desiredWidth,widthMeasureSpec));
        }
        else{
            int desiredHeight = getSuggestedMinimumHeight() + getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(measureDimension(desiredHeight,heightMeasureSpec), measureDimension(desiredHeight,heightMeasureSpec));
        }

    }

    @Override
    protected  void onDraw(Canvas canvas){
        int i = 0;

        if(tiles.size() == 0){
            createTileRectangles();
        }

        for (int c = 0; c < BoardUtils.NUMBER_OF_TILES_PER_ROW; c++) {
            for (int r = 0; r < BoardUtils.NUMBER_OF_TILES_PER_ROW; r++) {
                boardDirection.traverse(boardCells).get(i).setTileRect(tiles.get(i));
                boardDirection.traverse(boardCells).get(i).setColumn(c);
                boardDirection.traverse(boardCells).get(i).setRow(r);
                boardDirection.traverse(boardCells).get(i).draw(canvas);
                i++;
            }
        }
    }


    private int getCellWidth(int width){
        return  (width /8);
    }

    private int getCellHeight(int height){
        return  (height /8);
    }

    private int getXCoord(final int x) {
        int x0 = 0;
        return x0 + squareSize * (isFlipped ? x : 7 - x);
    }

    private int getYCoord(final int y) {
        int y0 = 0;
        return y0 + squareSize * (isFlipped ? y : 7 - y);
    }

    public void setDarkCellsColor(int dark) {
        this.darkCellsColor= dark;
        invalidate();
    }

    public void setWhiteCellsColor(int white) {
        this.whiteCellsColor= white;
        invalidate();
    }

    public int getWhiteCellsColor() {
        return whiteCellsColor;
    }

    public int getDarkCellsColor() {
        return darkCellsColor;
    }

    public void setMoveData(int from, int to) {
        if(matchAPI != null){
            matchAPI.sendMoveData(matchableAccount,from,to);
        }
    }

    public void setRemoteViewUpdateListener(RemoteViewUpdateListener remoteViewUpdateListener) {
        this.remoteViewUpdateListener = remoteViewUpdateListener;
    }

    @Override
    public void onRemoteMoveMade(RemoteMove remoteMove) {
        this.remoteViewUpdateListener.onRemoteMoveMade(remoteMove);
    }

    private enum BoardDirection {
        REVERSE{
            @Override
            List<Cell> traverse(final List<Cell> boardCells){
                return Lists.reverse(boardCells);
            }

            @Override
            BoardDirection opposite(){
                return NORMAL;
            }

        },NORMAL{
            @Override
            List<Cell> traverse(final List<Cell> boardCells){
                return boardCells;
            }

            @Override
            BoardDirection opposite(){
                return REVERSE;
            }
        };

        abstract BoardDirection opposite();
        abstract List<Cell> traverse(final List<Cell> boardCells);
    }

    public void restartChessBoard(){
        this.chessBoard = Board.createStandardBoard();
        invalidate();
    }


    public void flipBoardDirection() {
        this.boardDirection = boardDirection.opposite();
        topAlliance = topAlliance.equals(Alliance.BLACK) ? Alliance.WHITE : Alliance.BLACK;
        invalidate();
    }

    public void  setOnMoveDoneListener(final OnMoveDoneListener onMoveDoneListener){
        this.onMoveDoneListener = onMoveDoneListener;
    }

    public void setMatchableAccount(MatchableAccount matchableAccount) {
        // Makes sure to set match api to send and receive moves
        if(matchableAccount != null){
            this.matchableAccount = matchableAccount;
            matchAPI = MatchAPI.get();
            matchAPI.setRemoteMoveListener(this);
            matchAPI.getRemoteMoveData(matchableAccount);
            if(matchableAccount.getOpponent().equals("WHITE")){
                this.localAlliance = Alliance.BLACK;
            }
            else if(matchableAccount.getOpponent().equals("BLACK")){
                this.localAlliance = Alliance.WHITE;
            }
        }
    }

    public Player getCurrentPlayer(){
        return chessBoard.currentPlayer();
    }

    public void translateRemoteMoveOnBoard(RemoteMove remoteMove){
        if(remoteMove != null){
            final Move move = Move.MoveFactory.createMove(chessBoard,remoteMove.getFrom(),remoteMove.getTo());
            final MoveTransition transition = chessBoard.currentPlayer().makeMove(move);
            if(transition.getMoveStatus().isDone()){
                moveLog.addMove(move);
                moveCursor = moveLog.size();
                destinationTile = chessBoard.getTile(remoteMove.getTo());
                GameUtil.playSound();
                chessBoard = transition.getTransitionBoard();
                onMoveDoneListener.getMoves(moveLog);
                displayGameStates();
                invalidate();
            }
        }
    }

    public void reconstructBoard(List<Move> moves, Board board){
        this.moveLog.clear();
        this.chessBoard = board;
        for (Move move : moves){
            moveLog.addMove(move);
        }
        onMoveDoneListener.getMoves(moveLog);
//        GameUtil.playSound();
        moveCursor = moveLog.size();
        displayGameStates();
        invalidate();
    }

    public void undoMove(){
        this.sourceTile = null;
        this.destinationTile = null;
        this.movedPiece = null;

        if(this.moveCursor > 0){
            this.moveCursor -= 1;
            this.chessBoard = this.moveLog.getMove(moveCursor).undo();
            if(this.moveLog.getMove(moveCursor) instanceof Move.PawnEnPassantAttackMove){
                this.moveCursor -= 1;
                this.chessBoard = this.moveLog.getMove(moveCursor).undo();
            }
            displayGameStates();
            invalidate();
        }
    }
    public void redoMove(){
        if (this.moveCursor < moveLog.size()){
            // TODO Over kill (Fix from engine)
            Move move = Move.MoveFactory.createMove(chessBoard, moveLog.getMove(moveCursor).getCurrentCoordinate(), moveLog.getMove(moveCursor).getDestinationCoordinate());
            MoveTransition transition = chessBoard.currentPlayer().makeMove(move);
            if(transition.getMoveStatus().isDone()){
                chessBoard = transition.getTransitionBoard();
                displayGameStates();
                this.moveCursor += 1;
                invalidate();
            }
        }
    }
    private void displayGameStates(){
        if(chessBoard.currentPlayer().isInStaleMate()){
            onMoveDoneListener.isStaleMate(chessBoard.currentPlayer());
        }
        else if(chessBoard.currentPlayer().isInCheckMate()){
            onMoveDoneListener.isCheckMate(chessBoard.currentPlayer());
        }
        else if(chessBoard.currentPlayer().isInCheck()){
            onMoveDoneListener.isCheck(chessBoard.currentPlayer());
        }
        else if(chessBoard.isDraw()){
            onMoveDoneListener.isDraw();
        }
        else {
            onMoveDoneListener.onGameResume();
        }
    }

    public MoveLog getMoveLog() {
        return moveLog;
    }

    public MatchableAccount getMatchableAccount() {
        return matchableAccount;
    }

    public MatchAPI getMatchAPI() {
        return matchAPI;
    }

    public void setMatchAPI(MatchAPI matchAPI) {
        this.matchAPI = matchAPI;
    }
}


