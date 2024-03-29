package chessengine;

/*
  @author Collins Magondu 3/3/19
 */

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.chess.engine.Alliance;
import com.chess.engine.Move;
import com.chess.engine.board.Board;
import com.chess.engine.board.BoardUtils;
import com.chess.engine.board.Tile;
import com.chess.engine.pieces.Piece;
import com.chess.engine.player.MoveTransition;
import com.chess.engine.player.Player;
import com.chess.engine.player.chess_engine_ai.MinMaxAlgorithm;
import com.chess.engine.player.chess_engine_ai.MoveStategy;
import com.chess.pgn.FenUtilities;
import com.chess.pgn.PGNMainUtils;
import com.chess.pgn.PGNUtilities;
import com.crashlytics.android.Crashlytics;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

import chessbet.api.MatchAPI;
import chessbet.domain.MatchableAccount;
import chessbet.domain.Puzzle;
import chessbet.domain.RemoteMove;
import chessbet.services.RemoteMoveListener;
import chessbet.services.RemoteViewUpdateListener;
import chessbet.utils.GameTimer;
import stockfish.Engine;
import stockfish.EngineUtil;
import stockfish.Query;
import stockfish.QueryType;

public class BoardView extends View implements RemoteMoveListener, EngineUtil.OnResponseListener {
    private int boardSize; // In dp
    protected Board chessBoard;
    protected Tile sourceTile;
    protected Tile destinationTile;
    protected Piece movedPiece;
    private List<Cell> boardCells = null;
    private BoardDirection boardDirection;
    private Puzzle puzzle;
    protected int puzzleMoveCounter = 0;
    protected Alliance topAlliance = Alliance.BLACK;
    protected Modes mode = Modes.LOCAL_PLAY;
    private int squareSize = 0;
    private boolean isFlipped = false;
    private boolean isEnabled = true;
    private int whiteCellsColor;
    private int darkCellsColor;
    protected MoveLog moveLog;
    private boolean isRecording = false;
    protected GameTimer gameTimer;
    protected OnMoveDoneListener onMoveDoneListener;
    private MatchableAccount matchableAccount;
    private Alliance localAlliance;
    protected ECOBook ecoBook;
    private RemoteViewUpdateListener remoteViewUpdateListener;
    protected int moveCursor = 0;
    protected PuzzleMove puzzleMove;
    protected AI_ENGINE engine;
    protected boolean isHinting = false; // Is the board in hint mode
    protected boolean isEngineLoading = false;
    private List<Rect> tiles = new ArrayList<>();
    private MoveAnimator moveAnimator = new MoveAnimator();
    private EngineResponse engineResponse; // Used To get The Full Response String for Analysis Purpose
    protected volatile Move nextPuzzleMove = null;
    protected PromotionPieceListener promotionPieceListener;
    protected Piece.PieceType promotionPiece;
    private Board.PromotionListener promotionListener;

    // Stockfish 10
    private Engine stockfish;


    private volatile Move currentStockFishMove = null;
    private volatile Move ponderedStockFishMove = null;

    // Handles stockfish engine set up
    private void startStockfish() {
        try {
            // Start Stock Fish
            stockfish = new Engine();
            stockfish.start();
        } catch (Exception ex) {
            Crashlytics.logException(ex);
        }
    }

    private void initialize(Context context){

        ecoBook = new ECOBook();
        this.startStockfish();
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

    public void setPromotionListener(Board.PromotionListener promotionListener) {
        Board.setPromotionListener(promotionListener);
        this.promotionListener = promotionListener;
    }

    public void setPromotionPieceListener(PromotionPieceListener promotionPieceListener) {
        this.promotionPieceListener = promotionPieceListener;
    }

    public BoardView(Context context, AttributeSet attributeSet){
        super(context,attributeSet);
        initialize(context);
    }

    public BoardView(Context context){
        super(context);
       initialize(context);
    }

    @Override
    public boolean performClick(){
        super.performClick();
        return true;
    }

    public int getBoardSize() {
        return boardSize;
    }

    public Engine getStockfish(){
        return stockfish;
    }

    public Alliance getLocalAlliance() {
        return localAlliance;
    }

    public Board getChessBoard() {
        return chessBoard;
    }

    public void setChessBoard(Board chessBoard) {
        this.chessBoard = chessBoard;
        this.destinationTile = null; // Deselect destination tile
    }

    public void updateEcoView(){
        ecoBook.setMoveLog(moveLog.toString());
        ecoBook.startListening();
    }

    @Override
    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public void updateMoveView(){
        onMoveDoneListener.getMoves(moveLog);
    }

    public String getFen(){
        return FenUtilities.createFEN(chessBoard);
    }

    public void setPuzzleMove(PuzzleMove puzzleMove) {
        this.puzzleMove = puzzleMove;
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

    /**
     * Removes all flags for cells to be highlighted
     * @see Cell#handleTouch()
     */
    protected void clearTiles(){
            destinationTile = null;
            sourceTile = null;
            movedPiece = null;
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        performClick();
        if(event.getAction() == MotionEvent.ACTION_DOWN){
            final int x = (int) event.getX();
            final int y = (int) event.getY();
            for (int i = 0; i < BoardUtils.NUMBER_OF_TILES; i++) {
                if(boardCells.get(i).isTouched(x,y) && isEnabled){
                    if(matchableAccount != null){
                        if(isMyPly()){
                            boardCells.get(i).handleTouch();
                        }
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
                if(result == 0){
                    boardSize = specSize;
                }
                result = Math.min(result,specSize);
            }
        }

        if(result < desiredSize){
            Log.e("Board View","Too Small A Size");
        }

        // Make sure we get correct measurement for width and height
        if(result != 0){
            boardSize = result;
        }

        return Math.max(result, boardSize);
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
    protected void onDraw(Canvas canvas){
        int i = 0;
        moveAnimator.setCanvas(canvas);
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
        handleDrawHint();
    }

    public boolean isMyPly(){
        if(matchableAccount != null){
            return chessBoard.currentPlayer().getAlliance().equals(localAlliance);
        }
        return false;
    }

    /**
     * Enable move ponder feature by stockfish
     */
    public void handleDrawHint(){
        if(currentStockFishMove != null && isHinting){
            try {
                int bto, bfrom, pto = 0, pfrom = 0;
                if(ponderedStockFishMove != null){
                    if(ponderedStockFishMove.getMovedPiece() == null) {
                        ponderedStockFishMove = null;
                    }
                } else {
                    ponderedStockFishMove = null;
                }

                if(!isFlipped){
                    bto =  63 - currentStockFishMove.getDestinationCoordinate();
                    bfrom = 63 - currentStockFishMove.getCurrentCoordinate();

                    if(ponderedStockFishMove != null){
                        pto = 63 - ponderedStockFishMove.getDestinationCoordinate();
                        pfrom = 63 - ponderedStockFishMove.getCurrentCoordinate();
                    }

                } else {
                    bto = currentStockFishMove.getDestinationCoordinate();
                    bfrom = currentStockFishMove.getCurrentCoordinate();

                    if(ponderedStockFishMove != null){
                        pto = ponderedStockFishMove.getDestinationCoordinate();
                        pfrom = ponderedStockFishMove.getCurrentCoordinate();
                    }
                }

                moveAnimator.setDraw(isHinting);
                moveAnimator.setColor(Color.BLUE);
                moveAnimator.setToRect(tiles.get(bto));
                moveAnimator.setFromRect(tiles.get(bfrom));
                moveAnimator.dispatchDraw();

                if(ponderedStockFishMove != null){
                    moveAnimator.setColor(Color.GREEN);
                    moveAnimator.setToRect(tiles.get(pto));
                    moveAnimator.setFromRect(tiles.get(pfrom));
                    moveAnimator.dispatchDraw();
                }
            } catch (Exception ex){
                ex.printStackTrace();
            }
        }
    }

    public void requestHint() {
        isHinting = !isHinting;
        askStockFishBestMove();
        invalidate();
    }

    /**
     * Disables hinting
     */
    public void switchHintingOff(){
        isHinting = false;
    }

    /**
     * Allows for hinting if user redoes move
     */
    public void reHint() {
        if(isHinting) {
            askStockFishBestMove();
            invalidate();
        }
    }

    public boolean isHinting() {
        return isHinting;
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

    private Alliance getOwnerAlliance(){
        if(matchableAccount != null){
            return (localAlliance == Alliance.WHITE) ? Alliance.WHITE : Alliance.BLACK;
        }
        return Alliance.WHITE;
    }

    /**
     * Sends Move Data To Match
     * @param from fromCellCoordinate
     * @param to toCellCoordinate
     */
    public void setMoveData(int from, int to, String promotedPiece) {
            // Help regulate time between play
//          long timeLeft = (getOwnerAlliance() == Alliance.WHITE) ? gameTimer.getOwnerTimeLeft() : gameTimer.getOpponentTimeLeft();
            MatchAPI.get().sendMoveData(matchableAccount,from,to, getPortableGameNotation(), gameTimer.getOwnerTimeLeft(), promotedPiece);
    }

    public void setRemoteViewUpdateListener(RemoteViewUpdateListener remoteViewUpdateListener) {
        this.remoteViewUpdateListener = remoteViewUpdateListener;
    }

    @Override
    public void onRemoteMoveMade(RemoteMove remoteMove) {
        this.remoteViewUpdateListener.onRemoteMoveMade(remoteMove);
    }

    @Override
    public void onResponse(String moves) {
        String [] sMoves = moves.split(" ");
        if(sMoves.length == 1){
            // END OF GAME
            currentStockFishMove = getMoveByPositions(sMoves[0]);
        } else  if (sMoves.length > 1 ){
            currentStockFishMove = getMoveByPositions(sMoves[0]);
            ponderedStockFishMove = getMoveByPositions(sMoves[1]);
        }
        postInvalidate();
    }

    @Override
    public void onStockfishFullResponse(String response) {
        if(engineResponse != null){
            engineResponse.onEngineResponse(response);
        }
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
        isFlipped = !isFlipped;
        this.boardDirection = boardDirection.opposite();
        topAlliance = topAlliance.equals(Alliance.BLACK) ? Alliance.WHITE : Alliance.BLACK;
        postInvalidate();
    }

    public void  setOnMoveDoneListener(final OnMoveDoneListener onMoveDoneListener){
        this.onMoveDoneListener = onMoveDoneListener;
    }

    /**
     * Set match parameters
     * @param matchableAccount user matchable account
     */
    public void setMatchableAccount(MatchableAccount matchableAccount) {
        // Makes sure to set match api to send and receive moves
        if(matchableAccount != null){
            this.matchableAccount = matchableAccount;
            MatchAPI.get().setRemoteMoveListener(this);
            MatchAPI.get().getRemoteMoveData(matchableAccount);
            if(matchableAccount.getOpponent().equals("WHITE")){
                this.localAlliance = Alliance.BLACK;
            }
            else if(matchableAccount.getOpponent().equals("BLACK")){
                this.localAlliance = Alliance.WHITE;
            }
        }
    }

    public void setOnlineBoardDirection(){
        if(matchableAccount != null){
            if (localAlliance.isBlack()){
                this.boardDirection = BoardDirection.NORMAL;
            } else if (localAlliance.isWhite()){
                this.boardDirection = BoardDirection.REVERSE;
            }
        }
        invalidate();
    }

    public GameTimer getGameTimer() {
        return gameTimer;
    }

    public Player getCurrentPlayer(){
        return chessBoard.currentPlayer();
    }

    public boolean isLocalWinner(){
        if(chessBoard.currentPlayer().isInCheckMate()){
            return !chessBoard.currentPlayer().getAlliance().equals(localAlliance);
        }
        return false;
    }

    public void translateRemoteMoveOnBoard(RemoteMove remoteMove){
        if(remoteMove != null){
            final Move move = Move.MoveFactory.createMove(chessBoard,remoteMove.getFrom(),remoteMove.getTo());
            if(move.isPawnPromotionMove()) {
                // Take Back Control to method useful when making a promotion move
                Board.setPromotionListener(() -> getPromotedPieceFromString(remoteMove.getPromotedPiece()));
            }
            final MoveTransition transition = chessBoard.currentPlayer().makeMove(move);
            if(transition.getMoveStatus().isDone()){
                movedPiece = null;
                sourceTile = chessBoard.getTile(move.getCurrentCoordinate());
                destinationTile = chessBoard.getTile(move.getDestinationCoordinate());

                if(move.isCheckMateMove()){
                    move.setCheckMateMove(true);
                } else if (move.isCheckMove()) {
                    move.setCheckMove(true);
                }
                moveLog.addMove(move);

                // Listen for book moves from opponent
                this.ecoBook.setMoveLog(this.moveLog.toString());
                this.ecoBook.startListening();

                moveCursor = moveLog.size();
                GameUtil.playSound();
                if(gameTimer != null){
                    gameTimer.setOpponentTimeLeft((int) remoteMove.getGameTimeLeft());
                    gameTimer.setOpponentTimer();
                    gameTimer.stopTimer((chessBoard.currentPlayer().getAlliance() == Alliance.WHITE) ? chessbet.domain.Player.WHITE :  chessbet.domain.Player.BLACK);
                }
                chessBoard = transition.getTransitionBoard();
                onMoveDoneListener.getMoves(moveLog);
                // Return Back Control to Activity Implementing The Promotion Interface
                Board.setPromotionListener(this.promotionListener);
                displayGameStates();
                invalidate();
            }
        }
    }

    private boolean isOnOnlineGame() {
        return matchableAccount != null;
    }

    public void makeMove(Move move) {
        final MoveTransition transition = chessBoard.currentPlayer().makeMove(move);

        if(transition.getMoveStatus().isDone()){
            // Undo a move
            if(moveCursor < moveLog.size()){
                moveLog.removeMoves(moveCursor -1);
                onMoveDoneListener.getMoves(moveLog);
            }

            destinationTile = chessBoard.getTile(move.getDestinationCoordinate());

            // Promotion Piece
            String strPromotedPiece = ".";
            if (move.isPawnPromotionMove()) {
                strPromotedPiece = getPieceTypeFromPawnPromotion(move.toString()).toString();
            }

            if(isOnOnlineGame()) {
                setMoveData(movedPiece.getPiecePosition(), move.getDestinationCoordinate(),
                        (chessBoard.currentPlayer().getAlliance().equals(Alliance.BLACK) ? strPromotedPiece.toLowerCase() : strPromotedPiece.toUpperCase())); // Online Play
            }

            if(mode != BoardView.Modes.PUZZLE_MODE && !isEngineLoading) {
                // Stop the current player timer
                if(gameTimer != null){
                    gameTimer.stopTimer((chessBoard.currentPlayer().getAlliance() == Alliance.WHITE) ? chessbet.domain.Player.WHITE :  chessbet.domain.Player.BLACK);
                }

                // Ask stockfish
                chessBoard = transition.getTransitionBoard();

                GameUtil.playSound();  // Play sound once move is made

                setMoveCheckOrMate(move);

                if(isHinting){
                    askStockFishBestMove();
                }

                moveLog.addMove(move);
                updateEcoView();

                addMoveToPuzzle(move);
                moveCursor = moveLog.size();
                onMoveDoneListener.getMoves(moveLog);
                onMoveDoneListener.onGameResume();
            }
            else if (mode == BoardView.Modes.PUZZLE_MODE){
                // Check Move Validity
                puzzleModeAction(move);
            }

            if(mode == BoardView.Modes.PLAY_COMPUTER && chessBoard.currentPlayer().getAlliance() == Alliance.BLACK){
                isEngineLoading = true;
                Query query = new Query.Builder()
                        .setQueryType(QueryType.BEST_MOVE)
                        .setDepth(EngineUtil.getSkillLevel())
                        .setFen(getFen())
                        .setThreads(1)
                        .setTime(3000).build();
                EngineUtil.submit(query, response -> {
                    if(!response.isEmpty()){
                        Move sMove = getMoveByPositions(response.get(0));
                        MoveTransition moveTransition = chessBoard.currentPlayer().makeMove(sMove);
                        if(moveTransition.getMoveStatus().isDone()){
                            clearTiles();
                            destinationTile =  chessBoard.getTile(sMove.getDestinationCoordinate());
                            sourceTile = chessBoard.getTile(sMove.getCurrentCoordinate());
                            chessBoard = moveTransition.getTransitionBoard();
                            GameUtil.playSound();
                            updateGameStatus();
                            moveLog.addMove(sMove);
                            moveCursor = moveLog.size();
                            onMoveDoneListener.getMoves(moveLog);
                            isEngineLoading = false;
                            postInvalidate();
                        }
                    }
                });
            }
            updateGameStatus();
        } else {
            clearTiles();
        }
        invalidate();
    }

    private void updateGameStatus(){
        if(chessBoard.currentPlayer().isInCheckMate()){
            onMoveDoneListener.isCheckMate(chessBoard.currentPlayer());
        }
        else if(chessBoard.currentPlayer().isInCheck()){
            onMoveDoneListener.isCheck(chessBoard.currentPlayer());
        }
        else if(chessBoard.currentPlayer().isInStaleMate()){
            onMoveDoneListener.isStaleMate(chessBoard.currentPlayer());
        }
        else if(chessBoard.isDraw()){
            onMoveDoneListener.isDraw();
        } else {
            onMoveDoneListener.onGameResume();
        }
    }

    private void puzzleModeAction(Move move){
        if(puzzleMoveCounter < getPuzzle().getMoves().size() && isCorrectPuzzleMove(move)){
            puzzleMove.onCorrectMoveMade(true);
            puzzleMoveCounter++;
            invalidate();

            if(puzzleMoveCounter < getPuzzle().getMoves().size()){
                new Thread(() -> {
                    try {
                        // Wait for a second before the next move is made
                        Thread.sleep(1000);
                        clearTiles();
                        Move nextMove = Move.MoveFactory.createMove(chessBoard , getPuzzle().getMoves().get(puzzleMoveCounter).getFromCoordinate(),
                                getPuzzle().getMoves().get(puzzleMoveCounter).getToCoordinate());
                        destinationTile = chessBoard.getTile(nextMove.getDestinationCoordinate());
                        nextPuzzleMove = nextMove;
                        chessBoard = chessBoard.currentPlayer().makeMove(nextMove).getTransitionBoard();
                        destinationTile = chessBoard.getTile(nextMove.getDestinationCoordinate());
                        sourceTile = chessBoard.getTile(nextMove.getCurrentCoordinate());
                        puzzleMoveCounter++;
                        // Ensure board positions are redone
                        postInvalidate();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            }
            else{
                setMode(BoardView.Modes.LOCAL_PLAY);
                puzzleMove.onPuzzleFinished();
            }

            GameUtil.playSound();
            moveCursor = moveLog.size();
            onMoveDoneListener.getMoves(moveLog);
            onMoveDoneListener.onGameResume();
        } else {
            puzzleMove.onCorrectMoveMade(false);
        }
    }


    private boolean isCorrectPuzzleMove(Move move){
        Board board;
        if(getPuzzle().getMoves().get(puzzleMoveCounter).getToCoordinate() == move.getDestinationCoordinate()){
            if(getPuzzle().getMoves().get(puzzleMoveCounter).getFromCoordinate() == move.getCurrentCoordinate()){
                board = chessBoard.currentPlayer().makeMove(move).getTransitionBoard();
                if (board.currentPlayer().isInCheckMate()) {
                    move.setCheckMateMove(true);
                } else if (board.currentPlayer().isInCheck()) {
                    move.setCheckMove(true);
                }
                boolean correct = move.toString().equals(getPuzzle().getMoves().get(puzzleMoveCounter).getMoveCoordinates());
                if(correct){
                    chessBoard = board;
                    if(nextPuzzleMove != null){
                        moveLog.addMove(nextPuzzleMove);
                        nextPuzzleMove = null;
                    }
                    moveLog.addMove(move);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get promoted piece from Move String
     * @param moveString from Move.toString()
     * @return PieceType
     */
    public Piece.PieceType getPieceTypeFromPawnPromotion(String moveString) {
        String promotionPiece = moveString.split("=")[1];
        Log.d("Promotion Piece", promotionPiece);
        return this.getPromotedPieceFromString(promotionPiece);
    }

    public Piece.PieceType getPromotedPieceFromString(String string) {
        final String upperCaseString = string.toUpperCase();
        switch (upperCaseString) {
            case "N":
                return Piece.PieceType.KNIGHT;
            case "Q":
                return Piece.PieceType.QUEEN;
            case "R":
                return Piece.PieceType.ROOK;
            case "B":
                return Piece.PieceType.BISHOP;
        }
        return Piece.PieceType.QUEEN;
    }

    public void reconstructBoardFromPGN(String pgn){
        Board.setPromotionListener(() -> promotionPiece);
        List<String> strMoves = PGNMainUtils.processMoveText(pgn);
        for (String string : strMoves) {
            Move move = PGNMainUtils.createMove(chessBoard, string);
            if(move.isPawnPromotionMove()) {
                this.promotionPiece = getPieceTypeFromPawnPromotion(string);
            }
            MoveTransition moveTransition = chessBoard.currentPlayer().makeMove(move);
            if (moveTransition.getMoveStatus().isDone()) {
                chessBoard = moveTransition.getTransitionBoard();
                if (chessBoard.currentPlayer().isInCheckMate()) {
                    move.setCheckMateMove(true);
                } else if (chessBoard.currentPlayer().isInCheck()) {
                    move.setCheckMove(true);
                }
                moveLog.addMove(move);
            }
        }
        Board.setPromotionListener(this.promotionListener);
        onMoveDoneListener.getMoves(moveLog);
        moveCursor = moveLog.size();
        displayGameStates();
        invalidate();
    }

    protected Move getMoveByPositions(String position){
        String from = position.substring(0,2);
        String to = position.substring(2,4);
        int fromIndex = BoardUtils.getCoordinateAtPosition(from);
        int toIndex = BoardUtils.getCoordinateAtPosition(to);
        return Move.MoveFactory.createMove(chessBoard, fromIndex, toIndex);
    }

    /**
     * Asks stockfish for best move and ponder move
     */
    public void askStockFishBestMove() {
        Query query = new Query.Builder()
                .setQueryType(QueryType.BEST_MOVE)
                .setDepth(20)
                .setFen(getFen())
                .setThreads(1)
                .setTime(1000).build();
        EngineUtil.submit(query, response -> {
            if(response.size() == 1) {
                ponderedStockFishMove = null;
                currentStockFishMove = getMoveByPositions(response.get(0));
                postInvalidate();
            } else if (response.size() == 2) {
                currentStockFishMove = getMoveByPositions(response.get(0));
                ponderedStockFishMove = getMoveByPositions(response.get(1));
                postInvalidate();
            }
        });
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
            Board.setPromotionListener(() -> promotionPiece);
            final Move moveLogMove = moveLog.getMove(moveCursor);
            if(moveLogMove.isPawnPromotionMove()) {
                this.promotionPiece = getPieceTypeFromPawnPromotion(moveLog.getMove(moveCursor).toString());
            }
            Move move = Move.MoveFactory.createMove(chessBoard, moveLog.getMove(moveCursor).getCurrentCoordinate(), moveLog.getMove(moveCursor).getDestinationCoordinate());
            MoveTransition transition = chessBoard.currentPlayer().makeMove(move);
            if(transition.getMoveStatus().isDone()){
                chessBoard = transition.getTransitionBoard();
                displayGameStates();
                this.moveCursor += 1;
                invalidate();
            }
            Board.setPromotionListener(this.promotionListener);
        }
    }

    public void displayGameStates(){
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

    public boolean isGameDrawn(){
        return chessBoard.isDraw();
    }

    public MoveLog getMoveLog() {
        return moveLog;
    }

    public MatchableAccount getMatchableAccount() {
        return matchableAccount;
    }

    public void setGameTimer(GameTimer gameTimer) {
        this.gameTimer = gameTimer;
        if(gameTimer.getCurrentPlayer() != null){
            // Reset timer to the old time left
            setGameTimerTime(gameTimer.getOwnerTimeLeft(), gameTimer.getOpponentTimeLeft());
        } else {
            this.gameTimer.setOpponentTimeLeft((int) (this.matchableAccount.getDuration() * 60000));
            this.gameTimer.setOwnerTimeLeft((int) (this.matchableAccount.getDuration() * 60000));
            this.gameTimer.startTimer();
        }
    }

    private void setGameTimerTime(int white, int black) {
        this.gameTimer.setOpponentTimeLeft(black);
        this.gameTimer.setOwnerTimeLeft(white);
        if(this.gameTimer.getCurrentPlayer().getAlliance().equals(this.gameTimer.getOpponentAlliance())){
            this.gameTimer.setOpponentTimer();
        } else {
            this.gameTimer.setOwnerTimer();
        }
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void setRecording() {
        isRecording = !isRecording;
        // Lazy load puzzle only when recording
        this.puzzle = new Puzzle();
        // Sets the owner of the move will help in puzzle mode for the view
        this.puzzle.setPlayerType((chessBoard.currentPlayer().getAlliance() == Alliance.WHITE) ? "WHITE" : "BLACK");
        this.puzzle.setPgn(getPortableGameNotation());
    }

    public Puzzle getPuzzle() {
        return puzzle;
    }

    public void setPuzzle(Puzzle puzzle) {
        if(mode != Modes.PUZZLE_MODE){
            this.mode = Modes.PUZZLE_MODE;
            this.puzzle = puzzle;
            this.reconstructBoardFromPGN(puzzle.getPgn());
        }
    }

    /**
     * Adds a move to the puzzle moves array list by initializing a new puzzle move from the moves
     * needed params. See {@link Puzzle.Move}
     * In use by {@link Cell#handleTouch()}
     * @param move
     */
    protected void addMoveToPuzzle(Move move){
        if(this.isRecording){
            Puzzle.Move puzzleMove = new Puzzle.Move();
            puzzleMove.setFromCoordinate(move.getCurrentCoordinate());
            puzzleMove.setToCoordinate(move.getDestinationCoordinate());
            puzzleMove.setMoveCoordinates(move.toString());
            this.puzzle.addMove(puzzleMove);
        }
    }

    public enum Modes{
        LOCAL_PLAY,
        GAME_REVIEW,
        PLAY_ONLINE,
        PUZZLE_MODE,
        PLAY_COMPUTER,
        ANALYSIS
    }

    /**
     * Annotates if move is checkmate or check
     * @param move made move
     */
    protected void setMoveCheckOrMate(Move move) {
        if (chessBoard.currentPlayer().isInCheckMate()) {
            move.setCheckMateMove(true);
        } else if (chessBoard.currentPlayer().isInCheck()) {
            move.setCheckMove(true);
        }

    }

    public Modes getMode() {
        return mode;
    }

    public void setMode(Modes mode) {
        this.mode = mode;
    }

    // Convert Game To PGN Notation
    public String getPortableGameNotation(){
        return PGNUtilities.get().acceptMoveLog(this.moveLog.convertToEngineMoveLog());
    }

    public void setEcoBookListener(ECOBook.OnGetECOListener listener){
        this.ecoBook.setListener(listener);
    }

    public interface PuzzleMove{
        void onCorrectMoveMade(boolean isCorrect);
        void onPuzzleFinished();
    }

     protected interface EngineMoveHandler{
        void onBestMoveMade(Move move);
    }

    public interface EngineResponse{
        void onEngineResponse(String response);
    }

    public void highlightDestinationTile(int coordinate){
       destinationTile = chessBoard.getTile(coordinate);
    }

    // Enable Play Computer Engine as Black
     static class AI_ENGINE extends AsyncTask<Board,Void,Move> {
        private EngineMoveHandler engineMoveHandler;

        void setEngineMoveHandler(EngineMoveHandler engineMoveHandler) {
            this.engineMoveHandler = engineMoveHandler;
        }

        @Override
        protected Move doInBackground(Board... boards) {
            MoveStategy minMax = new MinMaxAlgorithm(2);
            return minMax.execute(boards[0]);
        }

        @Override
        protected void onPostExecute(Move move) {
            // END GAME MIGHT RETURN A NULL
            if(move != null){
                engineMoveHandler.onBestMoveMade(move);
            }
        }
    }
}


