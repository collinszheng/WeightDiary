package com.weightdiary.app.domain.chart

/**
 * Fritsch–Carlson **单调三次插值**。
 *
 * 为什么不用普通的 Catmull-Rom 或自然三次样条：那些会**过冲** ——
 * 在相邻两点之间甩出低于最小值或高于最大值的假峰假谷。
 * 体重曲线出现「凭空多出来的一次反弹」是不能接受的（见技术设计 §4.6）。
 *
 * 本类只做数学，不碰任何绘图 API；UI 侧把切线转成贝塞尔控制点后用 `Path.cubicTo` 画。
 */
object MonotoneCubic {

    /** 一段三次贝塞尔（由 Hermite 转来），坐标仍是「数据空间」，UI 再映射到像素 */
    data class Segment(
        val x0: Double,
        val y0: Double,
        val c1x: Double,
        val c1y: Double,
        val c2x: Double,
        val c2y: Double,
        val x1: Double,
        val y1: Double,
    )

    /**
     * 计算每个点上的切线斜率 `m[i] = dy/dx`。
     *
     * 点数为 0 或 1 时返回全 0（调用方应走退化分支）；
     * 点数为 2 时两点取同一条直线的斜率。
     */
    fun tangents(xs: List<Double>, ys: List<Double>): DoubleArray {
        require(xs.size == ys.size) { "xs 与 ys 长度必须一致" }
        val n = xs.size
        val m = DoubleArray(n)
        if (n < 2) return m

        if (n == 2) {
            val d = slope(xs[0], ys[0], xs[1], ys[1])
            m[0] = d
            m[1] = d
            return m
        }

        val deltas = DoubleArray(n - 1) { slope(xs[it], ys[it], xs[it + 1], ys[it + 1]) }
        m[0] = deltas[0]
        m[n - 1] = deltas[n - 2]

        for (i in 1 until n - 1) {
            val d0 = deltas[i - 1]
            val d1 = deltas[i]
            if (d0 * d1 <= 0.0) {
                // 极值点：切线置 0，曲线在这里「平一下」，不会冲过头
                m[i] = 0.0
            } else {
                val h0 = xs[i] - xs[i - 1]
                val h1 = xs[i + 1] - xs[i]
                val w1 = 2 * h1 + h0
                val w2 = h1 + 2 * h0
                m[i] = (w1 + w2) / (w1 / d0 + w2 / d1)
            }
        }
        return m
    }

    /**
     * 转成三次贝塞尔段，交给 `Path.cubicTo` 绘制。
     *
     * 比「每两点插 N 个采样点再 lineTo」更快也更平滑。
     */
    fun bezierSegments(xs: List<Double>, ys: List<Double>): List<Segment> {
        if (xs.size < 2) return emptyList()
        val m = tangents(xs, ys)
        return (0 until xs.size - 1).map { i ->
            val dx = xs[i + 1] - xs[i]
            val third = dx / 3.0
            Segment(
                x0 = xs[i],
                y0 = ys[i],
                c1x = xs[i] + third,
                c1y = ys[i] + m[i] * third,
                c2x = xs[i + 1] - third,
                c2y = ys[i + 1] - m[i + 1] * third,
                x1 = xs[i + 1],
                y1 = ys[i + 1],
            )
        }
    }

    /**
     * 按 [samplesPerSegment] 采样整条曲线。用于绘制退化分支与单测校验（比如「不过冲」）。
     */
    fun sample(
        xs: List<Double>,
        ys: List<Double>,
        samplesPerSegment: Int = 16,
    ): List<Pair<Double, Double>> {
        if (xs.isEmpty()) return emptyList()
        if (xs.size == 1) return listOf(xs[0] to ys[0])

        val out = mutableListOf<Pair<Double, Double>>()
        bezierSegments(xs, ys).forEach { s ->
            for (k in 0 until samplesPerSegment) {
                val t = k.toDouble() / samplesPerSegment
                out += bezierX(s, t) to bezierY(s, t)
            }
        }
        out += xs.last() to ys.last()
        return out
    }

    private fun bezierX(s: Segment, t: Double): Double = cubic(
        s.x0, s.c1x, s.c2x, s.x1, t,
    )

    private fun bezierY(s: Segment, t: Double): Double = cubic(
        s.y0, s.c1y, s.c2y, s.y1, t,
    )

    private fun cubic(p0: Double, p1: Double, p2: Double, p3: Double, t: Double): Double {
        val u = 1 - t
        return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3
    }

    private fun slope(x0: Double, y0: Double, x1: Double, y1: Double): Double {
        val dx = x1 - x0
        return if (dx == 0.0) 0.0 else (y1 - y0) / dx
    }
}
