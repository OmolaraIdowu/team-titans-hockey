package com.example.titans_hockey_challenge.ui.hockeytable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.MediaPlayer
import android.os.Handler
import android.os.Message
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.titans_hockey_challenge.R
import com.example.titans_hockey_challenge.viewmodels.LevelsDifficultySharedViewModel
import com.example.titans_hockey_challenge.models.Paddle
import com.example.titans_hockey_challenge.models.Puck
import com.example.titans_hockey_challenge.utils.GameThread
import com.example.titans_hockey_challenge.utils.PUCK_SPEED
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

    private var mediaPlayer: MediaPlayer? = null
    private var puckHitSound: MediaPlayer? = null
    private var winningSound: MediaPlayer? = null
    private var losingSound: MediaPlayer? = null
    private var wallHitSound: MediaPlayer? = null
    private var goalPostHitSound: MediaPlayer? = null
    var aiSpeed = 0.0f


    private fun initHockeyTable(ctx: Context, attr: AttributeSet?) {
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
        val strikerHeight = a.getInteger(R.styleable.HockeyTable_racketHeight, 140)
        val strikerWidth = a.getInteger(R.styleable.HockeyTable_racketWidth, 140)
        val puckRadius = a.getInteger(R.styleable.HockeyTable_puckRadius, 35)

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

        val puckInnerPaint = Paint()
        puckInnerPaint.color = ContextCompat.getColor(mContext!!, R.color.dark_gray)
        puck = Puck(puckRadius.toFloat(), puckPaint, puckInnerPaint)

        // Draw circular and middle line
        mNetPaint = Paint()
        mNetPaint!!.isAntiAlias = true
        mNetPaint!!.color = ContextCompat.getColor(mContext!!, R.color.forest_green)
//        mNetPaint!!.alpha = 100
        mNetPaint!!.style = Paint.Style.STROKE
        mNetPaint!!.strokeWidth = 8f

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
        mGoalPostBoundsPaint!!.style = Paint.Style.FILL
        mGoalPostBoundsPaint!!.strokeWidth = 35f
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (!game!!.isPaused) {
            canvas.drawColor(ContextCompat.getColor(mContext!!, R.color.table_color))

            // Draw Hockey board with rounded corners
            val cornerRadius = 50f
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
            canvas.drawLine(middle.toFloat(), 18f, middle.toFloat(), (mTableHeight - 18).toFloat(), mNetPaint!!)

            // Draw circular line
            val centerY = mTableHeight.toFloat() / 2
            val radius = minOf(middle, mTableHeight / 4) - 13f
            canvas.drawCircle(middle.toFloat(), centerY, radius, mNetPaint!!)
            canvas.drawCircle(middle.toFloat(), mTableHeight.toFloat() / 2, 25f, mGoalPostBoundsPaint!!)

            // Draw goal post line
            // left goal post
            val leftGoalPostX = 1f
            val goalPostY1 = centerY - radius
            val goalPostY2 = centerY + radius
            canvas.drawLine(leftGoalPostX, goalPostY1, leftGoalPostX, goalPostY2, mGoalPostBoundsPaint!!)
            canvas.drawCircle(leftGoalPostX, centerY, radius, mNetPaint!!)
            canvas.drawLine(430f, 18f, 430f, (mTableHeight - 18).toFloat(), mNetPaint!!)

            // right goal post
            val rightGoalPostX = mTableWidth.toFloat() - 1
            canvas.drawLine(rightGoalPostX, goalPostY1, rightGoalPostX, goalPostY2, mGoalPostBoundsPaint!!)
            canvas.drawCircle(rightGoalPostX, centerY, radius, mNetPaint!!)
            canvas.drawLine(mTableWidth - 430f, 18f, mTableWidth - 430f, (mTableHeight - 18).toFloat(), mNetPaint!!)

            game!!.setScoreText(
                paddle!!.score.toString(), mOpponent!!.score.toString()
            )
            paddle!!.drawCircle(canvas)
            mOpponent!!.drawCircle(canvas)
            puck!!.draw(canvas)
        }
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initHockeyTable(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initHockeyTable(context, attrs)
    }

    override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
        game!!.setRunning(true)
        game!!.start()
    }

    override fun surfaceChanged(surfaceHolder: SurfaceHolder, format: Int, width: Int, height: Int) {
        mTableWidth = width
        mTableHeight = height
        game!!.setUpNewRound()
    }

    override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
        var retry = true
        game!!.setRunning(false)

        pauseBackgroundSound()
        releaseSounds()

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
        val aiPaddle = mOpponent!!
        val puck = puck!!

        val initialY = mTableHeight / 2 - aiPaddle.requestHeight / 2
        val initialX = mTableWidth - aiPaddle.requestWidth - 2
        // distance -200 from the center of the table, which is the right
        val boundary = mTableWidth / 2 - 150f


        // Check if the puck is on the AI's side of the table (approaching the black goal line)
        if (puck.centerX > mTableWidth / 2) {
            // Calculate the desired AI paddle position based on the puck's position
            val desiredY = puck.centerY - aiPaddle.requestHeight / 2
            val desiredX = puck.centerX - aiPaddle.requestWidth / 2

            // Ensure that the AI paddle stays on its side of the table
            val maxMoveDistance = aiSpeed
            val deltaY = desiredY - aiPaddle.bounds.top
            val deltaX = desiredX - aiPaddle.bounds.left

            if (deltaY > maxMoveDistance) {
                // AI paddle is below the desired position, move it closer
                val newTop = aiPaddle.bounds.top + maxMoveDistance
                movePaddle(aiPaddle, aiPaddle.bounds.left, newTop)
            } else if (deltaY < -maxMoveDistance) {
                // AI paddle is above the desired position, move it closer
                val newTop = aiPaddle.bounds.top - maxMoveDistance
                movePaddle(aiPaddle, aiPaddle.bounds.left, newTop)
            } else {
                // The AI is close to the desired vertical position, let it move at max speed
                val newTop = aiPaddle.bounds.top + deltaY
                movePaddle(aiPaddle, aiPaddle.bounds.left, newTop)
            }

            if (deltaX > maxMoveDistance) {
                // AI paddle is to the right of the desired position, move it closer
                val newLeft = aiPaddle.bounds.left + maxMoveDistance
                movePaddle(aiPaddle, newLeft, aiPaddle.bounds.top)
            } else if (deltaX < -maxMoveDistance) {
                // AI paddle is to the left of the desired position, move it closer
                val newLeft = aiPaddle.bounds.left - maxMoveDistance
                movePaddle(aiPaddle, newLeft, aiPaddle.bounds.top)
            } else {
                // The AI is close to the desired horizontal position, let it move at max speed
                val newLeft = aiPaddle.bounds.left + deltaX
                movePaddle(aiPaddle, newLeft, aiPaddle.bounds.top)
            }

            // Check if the AI paddle is beyond the boundary and move it back
            if (aiPaddle.bounds.left < boundary) {
                movePaddle(aiPaddle, boundary, aiPaddle.bounds.top)
            }
        } else {
            // Puck is on the opposite side, move the AI paddle back to its initial position
            val deltaY = initialY - aiPaddle.bounds.top
            val deltaX = initialX - aiPaddle.bounds.left
            val maxMoveDistance = aiSpeed

            if (deltaY > maxMoveDistance) {
                // AI paddle is far from the initial vertical position, move it closer
                val newTop = aiPaddle.bounds.top + maxMoveDistance
                movePaddle(aiPaddle, aiPaddle.bounds.left, newTop)
            } else if (deltaY < -maxMoveDistance) {
                // AI paddle is far above the initial vertical position, move it closer
                val newTop = aiPaddle.bounds.top - maxMoveDistance
                movePaddle(aiPaddle, aiPaddle.bounds.left, newTop)
            } else {
                // The AI is close to its initial vertical position, let it move at max speed
                val newTop = aiPaddle.bounds.top + deltaY
                movePaddle(aiPaddle, aiPaddle.bounds.left, newTop)
            }

            if (deltaX > maxMoveDistance) {
                // AI paddle is to the right of the initial horizontal position, move it closer
                val newLeft = aiPaddle.bounds.left + maxMoveDistance
                movePaddle(aiPaddle, newLeft, aiPaddle.bounds.top)
            } else if (deltaX < -maxMoveDistance) {
                // AI paddle is to the left of the initial horizontal position, move it closer
                val newLeft = aiPaddle.bounds.left - maxMoveDistance
                movePaddle(aiPaddle, newLeft, aiPaddle.bounds.top)
            } else {
                // The AI is close to its initial horizontal position, let it move at max speed
                val newLeft = aiPaddle.bounds.left + deltaX
                movePaddle(aiPaddle, newLeft, aiPaddle.bounds.top)
            }
        }
    }

    fun update(canvas: Canvas?) {
        if (!game!!.isPaused) {
            if (checkCollisionPaddle(paddle, puck)) {
                handleCollision(paddle, puck)
            } else if (checkCollisionPaddle(mOpponent, puck)) {
                handleCollision(mOpponent, puck)
            } else if (checkCollisionWithTopOrBottomWall()) {
                // resets the puck's Y velocity
                puck!!.velocityY = -puck!!.velocityY
                playWallHitSound()
            } else if (checkCollisionWithLeftOrRightWall()) {
                // resets the puck's X velocity
                puck!!.velocityX = -puck!!.velocityX
                playWallHitSound()
            } else if (checkCollisionWithLeftGoalPost()) {
                game!!.setState(STATE_LOSE)
                playLosingSound()
                return
            } else if (checkCollisionWithRightGoalPost()) {
                game!!.setState(STATE_WIN)
                playWinningSound()
                return
            }
            if (Random(System.currentTimeMillis()).nextFloat() < mAiMoveProbability) doAI()
            puck!!.movePuck(canvas!!)
            doAI()
        }
    }

    private fun checkCollisionPaddle(paddle: Paddle?, puck: Puck?): Boolean {
        return paddle!!.bounds.intersects(puck!!.centerX - puck.radius, puck.centerY - puck.radius, puck.centerX + puck.radius, puck.centerY + puck.radius)
    }

    private fun checkCollisionWithTopOrBottomWall(): Boolean {
        return puck!!.centerY <= puck!!.radius || puck!!.centerY + puck!!.radius >= mTableHeight - 1
    }

    private fun checkCollisionWithLeftOrRightWall() : Boolean {
        return puck!!.centerX<= puck!!.radius || puck!!.centerX + puck!!.radius >= mTableWidth - 1
    }

    private fun checkCollisionWithLeftGoalPost() : Boolean {
        val goalPostX = 10f
        return puck!!.centerX - puck!!.radius <= goalPostX && puck!!.centerX + puck!!.radius >= goalPostX
    }

    private fun checkCollisionWithRightGoalPost() : Boolean {
        val goalPostX = mTableWidth - 10f
        return puck!!.centerX - puck!!.radius <= goalPostX && puck!!.centerX + puck!!.radius >= goalPostX
    }

    // TODO - WHEN I COME BACK TOMORROW MY TASKS ARE AS FOLLOWS :
    //  NUMBER 1 - TRY TO IMPROVE THE PHYSICS AND MOVEMENTS OF THE PUCK FOR A MORE REALISTIC EXPERIENCE.
    //  NUMBER 2 - TRY TO FIND THE ISSUE REGARDING THE GOAL IMPLEMENTATION WHERE SOMETIMES IT COUNTS AS A GOAL AND SOMETIMES, IT DOESN'T.
    //  NUMBER 3 - TRY AND SEE IF I CAN MAKE MORE IMPROVEMENTS TO THE LOOK OF TABLE IF MORE IDEAS COME.
    //  NUMBER 4 - EXPLORE THE POSSIBILITY OF ADDING ANOTHER GAME RULE(NUMBER OF ROUNDS TO BE DECLARED A WINNER) IF WE HAVE MORE TIME.

    private fun handleCollision(paddle: Paddle?, puck: Puck?) {
        // Reverses the X velocity which sorts of bounces it back
        puck!!.velocityX = -puck.velocityX

        // Adjust the Y velocity to maintain a constant speed
        val currentSpeed = Math.sqrt((puck.velocityX * puck.velocityX + puck.velocityY * puck.velocityY).toDouble())
        val targetSpeed = PUCK_SPEED // Adjust this to your desired speed
        val factor = targetSpeed / currentSpeed
        puck.velocityX *= factor.toFloat()
        puck.velocityY *= factor.toFloat()

        // Move the puck out of the paddle to prevent sticking
        if (paddle === this.paddle) {
            puck.centerX = paddle!!.bounds.right + puck.radius
        } else if (paddle === mOpponent) {
            puck.centerX = mOpponent!!.bounds.left - puck.radius
        }

        playPuckHitSound()
    }

    fun playStartGameSound() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(mContext, R.raw.soundtrack2)
        }
        mediaPlayer?.start()
    }

    fun pauseBackgroundSound() {
        if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
            mediaPlayer?.pause()
        }
    }

    fun stopBackgroundSound() {
        if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
            mediaPlayer?.stop()
            mediaPlayer = null
        }
    }

    private fun playWinningSound() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        if (winningSound == null) {
            playGoalPostHitSound()
            winningSound = MediaPlayer.create(mContext, R.raw.yay)
        }
        winningSound?.start()
    }

    private fun playLosingSound() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        if (losingSound == null) {
            playGoalPostHitSound()
            losingSound = MediaPlayer.create(mContext, R.raw.aww)
        }
        losingSound?.start()
    }

    private fun playGoalPostHitSound() {
        if (goalPostHitSound == null) {
            goalPostHitSound = MediaPlayer.create(mContext, R.raw.scoring)
        }
        goalPostHitSound?.start()
    }

    private fun playPuckHitSound() {
        if (puckHitSound == null) {
            puckHitSound = MediaPlayer.create(mContext, R.raw.hockey_puck_hit_sound_effect)
        }
        puckHitSound?.start()
    }

    private fun playWallHitSound() {
        if (wallHitSound == null) {
            wallHitSound = MediaPlayer.create(mContext, R.raw.hitting_wall)
        }
        wallHitSound?.start()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!game!!.sensorsOn()) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> if (game!!.isBetweenRounds) {
                    game!!.setState(STATE_RUNNING)

                    playStartGameSound()
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
                    playStartGameSound()
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
                val newLeft = paddle!!.bounds.left + dx
                val newTop = paddle.bounds.top + dy

                // defines the boundary that basically disallows the paddle from crossing
                // the center circle which is 130 units from half of the table
                val boundary = mTableWidth / 2 - 130f

                // this will then only move the paddle if the position doesn't(lesser then or equals too) cross the
                // boundary(130 units from the center)
                if (newLeft + paddle.requestWidth <= boundary) {
                    movePaddle(paddle, newLeft, newTop)
                }
            } else if (paddle === mOpponent)  {
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

    fun resumeGame() {
        if (game!!.isBetweenRounds) {
            game!!.setRunning(true)
            game!!.setState(STATE_RUNNING)
        }
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

    private fun releaseSounds() {
        puckHitSound?.release()
        winningSound?.release()
        losingSound?.release()
    }
}