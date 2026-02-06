package com.ccko.pikxplus.features;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Efficient canvas-based optical illusion view.
 * - Black background, white dots orbiting invisible anchors.
 * - Call start() to begin animation and stop() to end it.
 * 
 * - spacingDp to control density (smaller → more dots; larger → fewer) 
 *  and dotRadiusDp to control the dot size; 
 *  orbitRadiusDp controls how far each dot orbits its anchor.  
 */
public class OpticalIllusionView extends View implements Choreographer.FrameCallback {

	private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint bgPaint = new Paint();
	private final List<Particle> particles = new ArrayList<>();
	private final Random rnd = new Random();

	private boolean running = false;
	private long lastFrameNanos = 0L;
	private final Choreographer choreographer = Choreographer.getInstance();

	// Layout / density parameters (dp)
	private float spacingDp = 12f; // distance between anchors 
	private float orbitRadiusDp = 8f; // how far small dot orbits around anchor
	private float dotRadiusDp = 2f; // small dot radius

	// Converted to px
	private float spacingPx;
	private float orbitRadiusPx;
	private float dotRadiusPx;

	public OpticalIllusionView(Context context) {
		super(context);
		init(context);
	}

	public OpticalIllusionView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public OpticalIllusionView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context);
	}

	private void init(Context ctx) {
		bgPaint.setColor(Color.BLACK);
		dotPaint.setColor(Color.WHITE);
		dotPaint.setStyle(Paint.Style.FILL);

		DisplayMetrics dm = getResources().getDisplayMetrics();
		spacingPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, spacingDp, dm);
		orbitRadiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, orbitRadiusDp, dm);
		dotRadiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dotRadiusDp, dm);

		setWillNotDraw(false);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {

		buildParticles(w, h);
	}

	/**
		private void buildParticles(int width, int height) {
			particles.clear();
	
			// Compute number of columns/rows based on spacing
			int cols = Math.max(2, (int) (width / spacingPx));
			int rows = Math.max(2, (int) (height / spacingPx));
	
			// Center the grid by computing offsets
			float totalGridWidth = cols * spacingPx;
			float totalGridHeight = rows * spacingPx;
			float startX = (width - totalGridWidth) / 2f + spacingPx / 2f;
			float startY = (height - totalGridHeight) / 2f + spacingPx / 2f;
	
				float baseSpeed = 0.8f; // base angular speed (radians/sec)
			for (int r = 0; r < rows; r++) {
				for (int c = 0; c < cols; c++) {
					float cx = startX + c * spacingPx;
					float cy = startY + r * spacingPx;
	
					// Slight variation in orbit radius and speed for visual richness
					float orbit = orbitRadiusPx * (0.8f + 0.4f * rnd.nextFloat());
					float angle = rnd.nextFloat() * (float) (2 * Math.PI);
					// speed varies with position to create wave-like motion
					float speed = baseSpeed * (0.6f + 1.2f * rnd.nextFloat()) * (0.6f + 0.4f * ((c + r) % 7) / 7f);
					float phase = (c + r) * 0.08f; // staggered phase
	
					particles.add(new Particle(cx, cy, orbit, angle, speed, phase));
				}
			}
		}
	**/

	private void buildParticles(int width, int height) {
		particles.clear();

		// Expand grid slightly beyond the visible area (10% extra)
		final float extraPercent = 0.10f; // 0.05f for ~5%, 0.10f for ~10%
		int extendedWidth = Math.round(width * (1f + extraPercent));
		int extendedHeight = Math.round(height * (1f + extraPercent));

		// Compute number of columns/rows based on spacing using extended dimensions
		int cols = Math.max(2, (int) (extendedWidth / spacingPx));
		int rows = Math.max(2, (int) (extendedHeight / spacingPx));

		// Center the grid relative to the original viewport so extra anchors extend beyond edges
		float totalGridWidth = cols * spacingPx;
		float totalGridHeight = rows * spacingPx;
		// Start coordinates are computed so the grid is centered on the visible area,
		// which will place the extra rows/cols outside the visible bounds.
		float startX = (width - totalGridWidth) / 2f + spacingPx / 2f;
		float startY = (height - totalGridHeight) / 2f + spacingPx / 2f;

		// Keep density relevant
		// use spacingPx to approximate density on the device screen.
		float baseSpeed = 0.3f; // slowed base angular speed (radians/sec)
		for (int r = 0; r < rows; r++) {
			for (int c = 0; c < cols; c++) {
				float cx = startX + c * spacingPx;
				float cy = startY + r * spacingPx;

				// Make per-particle randomness ineffective 
				// orbit and speed are identical for all particles; initial angle encodes the start delay.
				float orbit = orbitRadiusPx; // identical orbit radius
				// Compute 50ms * column + 50ms * row
				float delayMs = 50f * c + 50f * r;
				float delaySec = delayMs / 1000f;
				// Set initial angle so particle appears as if it started later 
				float angle = -baseSpeed * delaySec;
				float speed = baseSpeed; // identical angular speed for all
				float phase = 0f; // no extra phase variation

				particles.add(new Particle(cx, cy, orbit, angle, speed, phase));
			}
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		// Black background
		canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

		// Draw each particle as a white dot at its computed position
		for (Particle p : particles) {
			float x = p.cx + (float) Math.cos(p.angle + p.phase) * p.orbit;
			float y = p.cy + (float) Math.sin(p.angle + p.phase) * p.orbit;
			canvas.drawCircle(x, y, dotRadiusPx, dotPaint);
		}
	}

	// Start animation
	public void start() {
		if (running)
			return;
		running = true;
		lastFrameNanos = 0L;
		choreographer.postFrameCallback(this);
	}

	// Stop animation
	public void stop() {
		if (!running)
			return;
		running = false;
		choreographer.removeFrameCallback(this);
	}

	@Override
	public void doFrame(long frameTimeNanos) {
		if (!running)
			return;

		if (lastFrameNanos == 0L)
			lastFrameNanos = frameTimeNanos;
		float dt = (frameTimeNanos - lastFrameNanos) / 1_000_000_000f; // seconds
		lastFrameNanos = frameTimeNanos;

		// Update particle angles
		for (Particle p : particles) {
			p.angle += p.speed * dt;
			// keep angle bounded
			if (p.angle > Math.PI * 2f)
				p.angle -= (float) (Math.PI * 2f);
		}

		// Request redraw and schedule next frame
		invalidate();
		choreographer.postFrameCallback(this);
	}

	// Particle data holder
	private static class Particle {
		final float cx, cy;
		final float orbit;
		float angle;
		final float speed;
		final float phase;

		Particle(float cx, float cy, float orbit, float angle, float speed, float phase) {
			this.cx = cx;
			this.cy = cy;
			this.orbit = orbit;
			this.angle = angle;
			this.speed = speed;
			this.phase = phase;
		}
	}
}