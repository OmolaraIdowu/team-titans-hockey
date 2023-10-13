package com.example.titans_hockey_challenge.models

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Message
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.titans_hockey_challenge.R
import com.example.titans_hockey_challenge.utils.GameThread
import com.example.titans_hockey_challenge.utils.PUCK_SPEED
import com.example.titans_hockey_challenge.utils.RACQUET_SPEED
import com.example.titans_hockey_challenge.utils.STATE_LOSE
import com.example.titans_hockey_challenge.utils.STATE_RUNNING
import com.example.titans_hockey_challenge.utils.STATE_WIN
import java.util.Random
import kotlin.math.abs

class HockeyTable : SurfaceView, SurfaceHolder.Callback {
    var game: GameThread? = null
        private set
    private var mStatus: TextView? = null
    private var mScorePlayer: TextView? = null
    private var mScoreOpponent: TextView? = null
    var paddle: Paddle? = null
        private set
    private var mOpponent: Paddle? = null
    var puck: Puck? = null
        private set
    private var mNetPaint: Paint? = null
    private var mGoalPostBoundsPaint: Paint? = null
    private var mTableBoundsPaint: Paint? = null
    private var mTableWidth = 0
    private var mTableHeight = 0
    private var mContext: Context? = null
    var mHolder: SurfaceHolder? = null
    private var mAiMoveProbability = 0f
    private var moving = false
    private var mLastTouchY = 0f
    private var mLastTouchX = 0f

    fun initHockeyTable(ctx: Context, attr: AttributeSet?) {
        mContext = ctx
        mHolder = holder
        mHolder!!.addCallback(this)
        game = GameThread(this.context, mHolder!!, this, object : Handler() {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                mStatus!!.visibility = msg.data.getInt("visibility")
                mStatus!!.text = msg.data.getString("text")
            }
        }, object : Handler() {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                mScorePlayer!!.text = msg.data.getString("player")
                mScoreOpponent!!.text = msg.data.getString("opponent")
            }
        })

        val a = ctx.obtainStyledAttributes(attr, R.styleable.HockeyTable)
        val strikerHeight = a.getInteger(R.styleable.HockeyTable_racketHeight, 150)
        val strikerWidth = a.getInteger(R.styleable.HockeyTable_racketWidth, 150)
        val puckRadius = a.getInteger(R.styleable.HockeyTable_puckRadius, 40)

        // Set Player
        val playerPaint = Paint()
        playerPaint.isAntiAlias = true
        playerPaint.color = ContextCompat.getColor(mContext!!, R.color.player_color)

        val playerMiddlePaint = Paint()
        playerMiddlePaint.isAntiAlias = true
        playerMiddlePaint.style = Paint.Style.FILL
        playerMiddlePaint.color = ContextCompat.getColor(mContext!!, R.color.player_middle_color)

        val playerOuterPaint = Paint()
        playerOuterPaint.style = Paint.Style.FILL
        playerOuterPaint.color = ContextCompat.getColor(mContext!!, R.color.player_outer_color)
        paddle = Paddle(strikerWidth, strikerHeight, paint = playerPaint, middlePaint = playerMiddlePaint, outerPaint = playerOuterPaint)

        // Set Opponent
        val opponentPaint = Paint()
        opponentPaint.isAntiAlias = true
        opponentPaint.color = ContextCompat.getColor(mContext!!, R.color.opponent_color)

        val opponentMiddlePaint = Paint()
        opponentMiddlePaint.isAntiAlias = true
        opponentMiddlePaint.style = Paint.Style.FILL
        opponentMiddlePaint.color = ContextCompat.getColor(mContext!!, R.color.opponent_middle_color)

        val opponentOuterPaint = Paint()
        opponentOuterPaint.style = Paint.Style.FILL
        opponentOuterPaint.color = ContextCompat.getColor(mContext!!, R.color.opponent_outer_color)
        mOpponent = Paddle(strikerWidth, strikerHeight, paint = opponentPaint, middlePaint = opponentMiddlePaint, outerPaint = opponentOuterPaint)

        // Set Puck
        val puckPaint = Paint()
        puckPaint.color = ContextCompat.getColor(mContext!!, R.color.puck_color)
        puck = Puck(puckRadius.toFloat(), puckPaint)

        // Draw circular and middle line
        mNetPaint = Paint()
        mNetPaint!!.isAntiAlias = true
        mNetPaint!!.color = Color.BLACK
        mNetPaint!!.alpha = 100
        mNetPaint!!.style = Paint.Style.STROKE
        mNetPaint!!.strokeWidth = 10f

        // Draw Bounds
        mTableBoundsPaint = Paint()
        mTableBoundsPaint!!.isAntiAlias = true
        mTableBoundsPaint!!.style = Paint.Style.STROKE
        mTableBoundsPaint!!.strokeWidth = 35f
        mAiMoveProbability = 0.8f

        // Draw Goal post
        mGoalPostBoundsPaint = Paint()
        mGoalPostBoundsPaint!!.isAntiAlias = true
        mGoalPostBoundsPaint!!.color = Color.BLACK
        mGoalPostBoundsPaint!!.strokeWidth = 38f
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        canvas.drawColor(ContextCompat.getColor(mContext!!, R.color.table_color))

        // Draw Hockey board with rounded corners
        val cornerRadius = 30f
        val rectF = RectF(0f, 0f, mTableWidth.toFloat(), mTableHeight.toFloat())
        val radii = floatArrayOf(
            cornerRadius, cornerRadius,
            cornerRadius, cornerRadius,
            cornerRadius, cornerRadius,
            cornerRadius, cornerRadius
        )

        val path = Path()
        path.addRoundRect(rectF, radii, Path.Direction.CW)
        canvas.drawPath(path, mTableBoundsPaint!!)

        // Draw middle line
        val middle = mTableWidth / 2
        canvas.drawLine(
            middle.toFloat(), 1f, middle.toFloat(), (mTableHeight - 1).toFloat(),
            mNetPaint!!
        )

        // Draw circular line
        val centerY = mTableHeight.toFloat() / 2
        val radius = minOf(middle, mTableHeight / 4) - 5f
        canvas.drawCircle(middle.toFloat(), centerY, radius, mNetPaint!!)

        // Draw goal post line
        // left goal post
        val leftGoalPostX = 10f
        val goalPostY1 = centerY - radius
        val goalPostY2 = centerY + radius
        canvas.drawLine(leftGoalPostX, goalPostY1, leftGoalPostX, goalPostY2, mGoalPostBoundsPaint!!)
        canvas.drawLine(430f, 1f, 430f, (mTableHeight - 1).toFloat(), mNetPaint!!)
        canvas.drawCircle(leftGoalPostX, centerY, radius, mNetPaint!!)

        // right goal post
        val rightGoalPostX = mTableWidth - 10f
        canvas.drawLine(rightGoalPostX, goalPostY1, rightGoalPostX, goalPostY2, mGoalPostBoundsPaint!!)
        canvas.drawLine(mTableWidth - 430f, 1f, mTableWidth - 430f, (mTableHeight - 1).toFloat(), mNetPaint!!)
        canvas.drawCircle(rightGoalPostX, centerY, radius, mNetPaint!!)

        // Todo - When I come back tomorrow, I will work on pausing the game state with the pause button,
        //  Second try to make the ai move in any direction but also towards the ball, Third add in game sound effects for the paddles and puck as well as in-game music.
        // Todo - I will also try to do these features in order of importance as respect to the limited time available

        game!!.setScoreText(
            paddle!!.score.toString(), mOpponent!!.score.toString()
        )
        paddle!!.drawCircle(canvas)
        mOpponent!!.drawCircle(canvas)
        puck!!.draw(canvas)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initHockeyTable(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        initHockeyTable(context, attrs)
    }

    override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
        game!!.setRunning(true)
        game!!.start()
    }

    override fun surfaceChanged(
        surfaceHolder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    ) {
        mTableWidth = width
        mTableHeight = height
        game!!.setUpNewRound()
    }

    override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
        var retry = true
        game!!.setRunning(false)
        while (retry) {
            try {
                game!!.join()
                retry = false
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    private fun doAI() {
        if (mOpponent!!.bounds.top > puck!!.centerY) {
            movePaddle(
                mOpponent,
                mOpponent!!.bounds.left,
                mOpponent!!.bounds.top - RACQUET_SPEED
            )
        } else if (mOpponent!!.bounds.top + mOpponent!!.requestHeight < puck!!.centerY) {
            movePaddle(
                mOpponent,
                mOpponent!!.bounds.left,
                mOpponent!!.bounds.top + RACQUET_SPEED
            )
        }
    }

    fun update(canvas: Canvas?) {
        if (checkCollisionPaddle(paddle, puck)) {
            handleCollision(paddle, puck)
        } else if (checkCollisionPaddle(mOpponent, puck)) {
            handleCollision(mOpponent, puck)
        } else if (checkCollisionWithTopOrBottomWall()) {
            puck!!.velocityY = -puck!!.velocityY
        } else if (checkCollisionWithLeftWall()) {
            game!!.setState(STATE_LOSE)
            return
        } else if (checkCollisionWithRightWall()) {
            game!!.setState(STATE_WIN)
            return
        }
        if (Random(System.currentTimeMillis()).nextFloat() < mAiMoveProbability) doAI()
        puck!!.movePuck(canvas!!)
        doAI()
    }

    private fun checkCollisionPaddle(paddle: Paddle?, puck: Puck?): Boolean {
        return paddle!!.bounds.intersects(
            puck!!.centerX - puck.radius,
            puck.centerY - puck.radius,
            puck.centerX + puck.radius,
            puck.centerY + puck.radius
        )
    }

    private fun checkCollisionWithTopOrBottomWall(): Boolean {
        return puck!!.centerY <= puck!!.radius || puck!!.centerY + puck!!.radius >= mTableHeight - 1
    }

    private fun checkCollisionWithLeftWall(): Boolean {
        return puck!!.centerX <= puck!!.radius
    }

    private fun checkCollisionWithRightWall(): Boolean {
        return puck!!.centerX + puck!!.radius >= mTableWidth - 1
    }

    private fun handleCollision(paddle: Paddle?, puck: Puck?) {
        puck!!.velocityX = -puck.velocityX * 1.05f
        if (paddle === this.paddle) {
            puck.centerX = paddle!!.bounds.right + puck.radius
        } else if (paddle === mOpponent) {
            puck.centerX = mOpponent!!.bounds.left - puck.radius
            RACQUET_SPEED *= 1.05f
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!game!!.sensorsOn()) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> if (game!!.isBetweenRounds) {
                    game!!.setState(STATE_RUNNING)
                } else {
                    if (isTouchOnRacket(event, paddle)) {
                        moving = true
                        mLastTouchX = event.x
                        mLastTouchY = event.y
                    }
                }
                MotionEvent.ACTION_MOVE -> if (moving) {
                    val x = event.x
                    val y = event.y
                    val dx = x - mLastTouchX
                    val dy = y - mLastTouchY
                    mLastTouchX = x
                    mLastTouchY = y
                    movePaddleStriker(dx, dy, paddle)
                }
                MotionEvent.ACTION_UP -> moving = false
            }
        } else {
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (game!!.isBetweenRounds) {
                    game!!.setState(STATE_RUNNING)
                }
            }
        }
        invalidate()
        return true
    }

    private fun isTouchOnRacket(event: MotionEvent, mPaddle: Paddle?): Boolean {
        return mPaddle!!.bounds.contains(event.x, event.y)
    }

    private fun movePaddleStriker(dx: Float, dy: Float, paddle: Paddle?) {
        synchronized(mHolder!!) {
            if (paddle === this.paddle) {
                movePaddle(paddle, paddle!!.bounds.left + dx, paddle.bounds.top + dy)
            } else if (paddle === mOpponent) {
                movePaddle(paddle, paddle!!.bounds.left, paddle.bounds.top + dy)
            }
        }
    }

    @Synchronized
    fun movePaddle(paddle: Paddle?, left: Float, top: Float) {
        var left = left
        var top = top
        if (left < 2) {
            left = 2f
        } else if (left + paddle!!.requestWidth >= mTableWidth - 2) {
            left = (mTableWidth - paddle.requestWidth - 2).toFloat()
        }
        if (top < 0) {
            top = 0f
        } else if (top + paddle!!.requestHeight >= mTableHeight) {
            top = (mTableHeight - paddle.requestHeight - 1).toFloat()
        }
        paddle!!.bounds.offsetTo(left, top)
    }

    fun setupTable() {
        placePuck()
        placePaddles()
    }

    private fun placePaddles() {
        paddle!!.bounds.offsetTo(2f, ((mTableHeight - paddle!!.requestHeight) / 2).toFloat())
        mOpponent!!.bounds.offsetTo(
            (mTableWidth - mOpponent!!.requestWidth).toFloat()
                    - 2,
            ((mTableHeight - mOpponent!!.requestHeight) / 2).toFloat()
        )
    }

    private fun placePuck() {
        puck!!.centerX = (mTableWidth / 2).toFloat()
        puck!!.centerY = (mTableHeight / 2).toFloat()
        puck!!.velocityY = puck!!.velocityY / abs(puck!!.velocityY) * PUCK_SPEED
        puck!!.velocityX = puck!!.velocityX / abs(puck!!.velocityX) * PUCK_SPEED
    }

    fun getMOpponent(): Paddle? {
        return mOpponent
    }

    fun setScorePlayer(view: TextView?) {
        mScorePlayer = view
    }

    fun setScoreOpponent(view: TextView?) {
        mScoreOpponent = view
    }

    fun setStatus(view: TextView?) {
        mStatus = view
    }

    fun setTableBoundsColor(color: Int) {
        mTableBoundsPaint!!.color = color
    }
}