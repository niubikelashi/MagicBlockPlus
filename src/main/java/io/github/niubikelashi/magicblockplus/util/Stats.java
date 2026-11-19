package io.github.niubikelashi.magicblockplus.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** 轻量统计：只计数，不做计时/采样（原版的 PerformanceMonitor 每个物理事件都计时，热路径开销大）。 */
public class Stats {

    private final AtomicLong places = new AtomicLong();
    private final AtomicLong breaks = new AtomicLong();
    private final AtomicLong gives = new AtomicLong();
    private final AtomicLong retrievals = new AtomicLong();
    private final AtomicLong consumptions = new AtomicLong();
    private final AtomicLong explosions = new AtomicLong();
    private final AtomicLong pistonsBlocked = new AtomicLong();
    private final AtomicLong physicsSkipped = new AtomicLong();
    private final AtomicLong depleted = new AtomicLong();
    private final AtomicLong guiOpens = new AtomicLong();
    private final AtomicLong guiClicks = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();

    public void place() { places.incrementAndGet(); }
    public void aBreak() { breaks.incrementAndGet(); }
    public void give() { gives.incrementAndGet(); }
    public void retrieve() { retrievals.incrementAndGet(); }
    public void consume() { consumptions.incrementAndGet(); }
    public void explosion() { explosions.incrementAndGet(); }
    public void pistonBlocked() { pistonsBlocked.incrementAndGet(); }
    public void physicsSkipped() { physicsSkipped.incrementAndGet(); }
    public void depleted() { depleted.incrementAndGet(); }
    public void guiOpen() { guiOpens.incrementAndGet(); }
    public void guiClick() { guiClicks.incrementAndGet(); }
    public void error() { errors.incrementAndGet(); }

    public Map<String, Long> snapshot() {
        Map<String, Long> out = new LinkedHashMap<>();
        out.put("放置", places.get());
        out.put("破坏", breaks.get());
        out.put("发放", gives.get());
        out.put("取回", retrievals.get());
        out.put("吃食", consumptions.get());
        out.put("爆炸处理", explosions.get());
        out.put("活塞拦截", pistonsBlocked.get());
        out.put("物理事件跳过", physicsSkipped.get());
        out.put("次数用尽", depleted.get());
        out.put("GUI 打开", guiOpens.get());
        out.put("GUI 点击", guiClicks.get());
        out.put("异常", errors.get());
        return out;
    }
}
