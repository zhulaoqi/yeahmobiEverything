package com.yeahmobi.everything.opsos.notify;

import com.yeahmobi.everything.common.Config;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Unified notification hub with multi-channel fanout.
 */
public class NotifyHub {

    private final Map<NotifyChannelType, NotifyChannel> channels;

    public NotifyHub(Map<NotifyChannelType, NotifyChannel> channels) {
        this.channels = channels == null ? Map.of() : channels;
    }

    public static NotifyHub fromConfig(Config config) {
        Map<NotifyChannelType, NotifyChannel> map = new EnumMap<>(NotifyChannelType.class);
        map.put(NotifyChannelType.EMAIL, new EmailNotifyChannel(config));
        map.put(NotifyChannelType.FEISHU, new FeishuNotifyChannel(config));
        return new NotifyHub(map);
    }

    public NotifyDispatchResult send(List<NotifyChannelType> targetChannels, String emailTarget, NotifyMessage message) {
        List<NotifyResult> results = new ArrayList<>();
        List<NotifyChannelType> channelTypes = (targetChannels == null || targetChannels.isEmpty())
                ? List.of(NotifyChannelType.EMAIL)
                : targetChannels;
        for (NotifyChannelType type : channelTypes) {
            NotifyChannel channel = channels.get(type);
            if (channel == null) {
                results.add(new NotifyResult(type, false, "通道不可用"));
                continue;
            }
            String target = type == NotifyChannelType.EMAIL ? emailTarget : "";
            results.add(channel.send(target, message));
        }
        boolean ok = results.stream().allMatch(NotifyResult::success);
        return new NotifyDispatchResult(ok, results);
    }
}
