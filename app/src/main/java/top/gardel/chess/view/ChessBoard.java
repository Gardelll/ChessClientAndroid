package top.gardel.chess.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class ChessBoard extends View {
    private static final String TAG = "ChessBoard";
    private final Chess[][] boardData;
    private final Paint paint;
    private final float defaultStrokeWidth = 5.0f;
    private final Path path;
    private OnClickChessGridListener onClickChessGridListener;

    public ChessBoard(Context context) {
        this(context, null);
    }

    public ChessBoard(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        boardData = new Chess[3][3];
        for (Chess[] boardDatum : boardData) {
            Arrays.fill(boardDatum, Chess.EMPTY);
        }
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStrokeWidth(defaultStrokeWidth);
        path = new Path();
        onClickChessGridListener = null;
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                v.performClick();
                if (event.getAction() == MotionEvent.ACTION_DOWN && onClickChessGridListener != null) {
                    float x = event.getX();
                    float y = event.getY();
                    Log.d(TAG, String.format("click ... x: %f, y: %f", x, y));
                    int left = v.getLeft();
                    int right = v.getRight();
                    int top = v.getTop();
                    int bottom = v.getBottom();
                    int w = Math.abs(right - left);
                    int h = Math.abs(bottom - top);
                    int l = (int) (Math.min(w, h) - defaultStrokeWidth * 10);
                    // fixme 点击右边缘算为下一格
                    Log.d(TAG, String.format("left: %d, top: %d, bottom: %d, right: %s, width: %s, height: %s", left, top, bottom, right, w, h));
                    int grid = l / boardData.length;
                    int posX = (int) (x / grid) + 1;
                    int posY = (int) (y / grid) + 1;
                    return onClickChessGridListener.onClick(ChessBoard.this, posX, posY);
                }
                return false;
            }
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int left = getLeft();
        int right = getRight();
        int top = getTop();
        int bottom = getBottom();
        int w = Math.abs(right - left);
        int h = Math.abs(bottom - top);
        int l = (int) (Math.min(w, h) - defaultStrokeWidth * 10);
        int n = boardData.length;
        int grid = l / n;

        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(left, top, left + l, top + l, paint);

        for (int i = 1; i <= n; i++)
            canvas.drawLine(left + grid * i, top, left + grid * i, top + l, paint);

        for (int i = 1; i <= n; i++)
            canvas.drawLine(left, top + grid * i, left + l, top + grid * i, paint);

        for (int i = 0; i < boardData.length; i++) {
            Chess[] chess = boardData[i];
            for (int j = 0; j < chess.length; j++) {
                Chess chess1 = chess[j];
                drawChess(canvas, left, top, l, n, i + 1, j + 1, chess1);
            }
        }

    }

    /**
     * 绘制棋子
     *
     * @param canvas 画板
     * @param x0     原点 x
     * @param y0     原点 y
     * @param l      棋盘像素
     * @param n      棋盘尺寸
     * @param posX   行
     * @param posY   列
     * @param chess  先手 or 后手
     */
    private void drawChess(Canvas canvas, int x0, int y0, int l, int n, int posX, int posY, Chess chess) {
        if (chess == Chess.EMPTY) return;
        int grid = l / n;
        int x = x0 + grid * posX - grid / 2;
        int y = y0 + grid * posY - grid / 2;
        path.reset();
        float wDiv2 = grid * 0.67f / 2;
        switch (chess) {
            case FIRSTHAND:
                path.addCircle(x, y, wDiv2, Path.Direction.CW);
                break;
            case BACKHAND:
                path.moveTo(x - wDiv2, y - wDiv2);
                path.lineTo(x + wDiv2, y + wDiv2);
                path.moveTo(x + wDiv2, y - wDiv2);
                path.lineTo(x - wDiv2, y + wDiv2);
                break;
        }
        paint.setStrokeWidth(defaultStrokeWidth * 2);
        canvas.drawPath(path, paint);
        paint.setStrokeWidth(defaultStrokeWidth);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int l = Math.min(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec), getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
        setMeasuredDimension(l, l);
    }

    public void setOnClickChessGridListener(@NonNull OnClickChessGridListener onClickChessGridListener) {
        this.onClickChessGridListener = onClickChessGridListener;
    }

    /**
     * 获取棋子
     *
     * @param x [1, 3]
     * @param y [1, 3]
     * @return Chess
     */
    @NonNull
    public Chess getChessAt(int x, int y) {
        checkPoint(x, y);
        return boardData[x - 1][y - 1];
    }

    public boolean hasChess(int x, int y) {
        return getChessAt(x, y) != Chess.EMPTY;
    }

    public boolean putChess(@NonNull Chess chess, int x, int y) {
        if (hasChess(x, y) && chess != Chess.EMPTY) return false;
        if (checkWinner() != Chess.EMPTY) return false;
        boardData[x - 1][y - 1] = chess;
        invalidate();
        return true;
    }

    public Chess[][] getBoardData() {
        return boardData;
    }

    public void setBoardData(Chess[][] data) {
        System.arraycopy(data, 0, boardData, 0, boardData.length);
        invalidate();
    }

    public void reset() {
        for (int i = 0; i < boardData.length; i++) {
            for (int j = 0; j < boardData[0].length; j++) {
                boardData[i][j] = Chess.EMPTY;
            }
        }
        invalidate();
    }

    @NonNull
    public Chess checkWinner() {
        for (Chess[] chess : boardData) {
            Chess lastChess = chess[0];
            byte connected = 1;
            for (int j = 1; j < chess.length; j++) {
                if (lastChess != Chess.EMPTY) {
                    if (chess[j] == lastChess) connected++;
                    else connected = 1;
                }
                lastChess = chess[j];
            }
            if (connected == 3 && lastChess != Chess.EMPTY) return lastChess;
        }
        for (int j = 0; j < boardData[0].length; j++) {
            Chess lastChess = boardData[0][j];
            byte connected = 1;
            for (int i = 1; i < boardData.length; i++) {
                if (lastChess != Chess.EMPTY) {
                    if (boardData[i][j] == lastChess) connected++;
                    else connected = 1;
                }
                lastChess = boardData[i][j];
            }
            if (connected == 3 && lastChess != Chess.EMPTY) return lastChess;
        }
        {
            Chess lastChess = boardData[0][0];
            byte connected = 1;
            for (int i = 1, j = 1; i < boardData.length && j < boardData[0].length; j = ++i) {
                if (lastChess != Chess.EMPTY) {
                    if (boardData[i][j] == lastChess) connected++;
                    else connected = 1;
                }
                lastChess = boardData[i][j];
            }
            if (connected == 3 && lastChess != Chess.EMPTY) return lastChess;
        }
        {
            Chess lastChess = boardData[0][2];
            byte connected = 1;
            for (int i = 1, j = 1; i < boardData.length && j >= 0; j--, i++) {
                if (lastChess != Chess.EMPTY) {
                    if (boardData[i][j] == lastChess) connected++;
                    else connected = 1;
                }
                lastChess = boardData[i][j];
            }
            if (connected == 3 && lastChess != Chess.EMPTY) return lastChess;
        }
        return Chess.EMPTY;
    }

    protected void checkPoint(int x, int y) {
        if (x < 1 || x > 3 || y < 1 || y > 3)
            throw new IllegalArgumentException(String.format("x = %d, y = %d", x, y));
    }

    @NotNull
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < boardData[0].length * 2 + 1; i++) {
            sb.append('-');
        }
        sb.append('\n');
        for (Chess[] chess : boardData) {
            sb.append('|');
            for (Chess chess1 : chess) {
                switch (chess1) {
                    case FIRSTHAND:
                        sb.append("O|");
                        break;
                    case BACKHAND:
                        sb.append("X|");
                        break;
                    default:
                        sb.append(" |");
                        break;
                }
            }
            sb.append('\n');
            for (int i = 0; i < boardData[0].length * 2 + 1; i++) {
                sb.append('-');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public enum Chess {
        EMPTY,
        FIRSTHAND,
        BACKHAND
    }

    public interface OnClickChessGridListener {
        boolean onClick(@NonNull ChessBoard chessBoard, int x, int y);
    }

}
