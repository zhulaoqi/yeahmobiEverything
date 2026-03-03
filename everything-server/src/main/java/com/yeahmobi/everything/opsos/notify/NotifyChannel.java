package com.yeahmobi.everything.opsos.notify;

/**
 * Abstract notification channel contract.
 */
public interface NotifyChannel {

    NotifyChannelType type();

    NotifyResult send(String target, NotifyMessage message);
}
