package com.yeahmobi.everything.workfollowup;

import com.yeahmobi.everything.common.Config;
import com.yeahmobi.everything.opsos.notify.NotifyChannelType;
import com.yeahmobi.everything.opsos.notify.NotifyDispatchResult;
import com.yeahmobi.everything.opsos.notify.NotifyHub;
import com.yeahmobi.everything.opsos.notify.NotifyMessage;
import com.yeahmobi.everything.opsos.trigger.TriggerDecision;
import com.yeahmobi.everything.opsos.trigger.TriggerEngine;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Sends test reminder emails on demand for acceptance checks.
 */
public final class WorkReminderEmailTestSender {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private WorkReminderEmailTestSender() {
    }

    public static SendResult sendTest(Config config, String toEmail, WorkTodo todo, int leadMinutes, String channelsCsv) {
        if (config == null) {
            return new SendResult(false, "配置不可用");
        }
        if (todo == null) {
            return new SendResult(false, "任务为空");
        }
        TriggerDecision trigger = new TriggerEngine().manualTest(todo.id(), System.currentTimeMillis());
        if (!trigger.fire()) {
            return new SendResult(false, "手动测试触发失败");
        }
        String now = LocalDateTime.now().format(DT);
        String due = todo.dueAt() == null || todo.dueAt().isBlank() ? "未设置" : todo.dueAt();
        String detail = "任务：" + safe(todo.title())
                + "\n截止：" + safe(due)
                + "\n提醒策略：提前 " + Math.max(1, leadMinutes) + " 分钟"
                + "\n发送时间：" + now;
        NotifyMessage message = new NotifyMessage(
                "manual-test",
                "【测试】待办提醒：" + safe(todo.title()),
                "手动测试通知链路",
                detail,
                "<html><body><h3>提醒测试</h3><pre>" + detail + "</pre></body></html>"
        );
        NotifyDispatchResult result = NotifyHub.fromConfig(config).send(parseChannels(channelsCsv), toEmail, message);
        if (result.success()) {
            return new SendResult(true, "测试通知已发送（Email+飞书）");
        }
        StringBuilder sb = new StringBuilder("部分通道发送失败：");
        result.channelResults().forEach(r -> sb.append(r.channel().name().toLowerCase(Locale.ROOT))
                .append("=")
                .append(r.success() ? "ok" : "fail")
                .append(" "));
        return new SendResult(false, sb.toString().trim());
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<NotifyChannelType> parseChannels(String csv) {
        String text = csv == null ? "" : csv.trim().toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return List.of(NotifyChannelType.EMAIL, NotifyChannelType.FEISHU);
        }
        java.util.ArrayList<NotifyChannelType> out = new java.util.ArrayList<>();
        for (String part : text.split("[,，\\s]+")) {
            if ("email".equals(part) && !out.contains(NotifyChannelType.EMAIL)) {
                out.add(NotifyChannelType.EMAIL);
            } else if ("feishu".equals(part) && !out.contains(NotifyChannelType.FEISHU)) {
                out.add(NotifyChannelType.FEISHU);
            }
        }
        if (out.isEmpty()) {
            out.add(NotifyChannelType.EMAIL);
        }
        return out;
    }

    public record SendResult(boolean success, String message) {
    }
}
