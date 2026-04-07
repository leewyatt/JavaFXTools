package io.github.leewyatt.fxtools.notification;

import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.io.HttpRequests;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.settings.FxToolsSettingsState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Application-level service that periodically checks JFX-Central's RSS feed
 * for new "Links of the Week" entries and shows a balloon notification.
 */
@Service(Service.Level.APP)
public final class JfxLinksNotifierService implements Disposable {

    private static final String RSS_URL = "https://www.jfx-central.com/lotw/rss.xml";
    private static final int INITIAL_DELAY_SECONDS = 20;

    private ScheduledFuture<?> scheduledTask;

    public static JfxLinksNotifierService getInstance() {
        return ApplicationManager.getApplication().getService(JfxLinksNotifierService.class);
    }

    public void start() {
        if (!FxToolsSettingsState.getInstance().enableLinksNotification) {
            return;
        }
        scheduleNextCheck(INITIAL_DELAY_SECONDS);
    }

    private void scheduleNextCheck(long delaySecs) {
        if (!FxToolsSettingsState.getInstance().enableLinksNotification) {
            return;
        }
        scheduledTask = AppExecutorUtil.getAppScheduledExecutorService()
                .schedule(this::checkAndNotify, delaySecs, TimeUnit.SECONDS);
    }

    private void checkAndNotify() {
        try {
            if (!FxToolsSettingsState.getInstance().enableLinksNotification) {
                return;
            }
            RssItem item = fetchLatestItem();
            if (item != null) {
                JfxLinksNotifierState state = JfxLinksNotifierState.getInstance();
                if (state.isNew(item.guid())) {
                    showNotification(item);
                    state.markNotified(item.guid());
                }
            }
        } finally {
            long nextDelayHours = getNextCheckDelayHours();
            scheduleNextCheck(nextDelayHours * 3600L);
        }
    }

    // ==================== Polling Frequency ====================

    private long getNextCheckDelayHours() {
        ZonedDateTime nowGmt = ZonedDateTime.now(ZoneOffset.UTC);
        DayOfWeek day = nowGmt.getDayOfWeek();
        int hour = nowGmt.getHour();

        // JFX-Central publishes around GMT Friday 17:00.
        // High-frequency window: GMT Friday 12:00 ~ Saturday 12:00 (24h).
        boolean inHighFreqWindow =
                (day == DayOfWeek.FRIDAY && hour >= 12) ||
                (day == DayOfWeek.SATURDAY && hour < 12);

        return inHighFreqWindow ? 1 : 24;
    }

    // ==================== RSS Fetch & Parse ====================

    @Nullable
    private RssItem fetchLatestItem() {
        try {
            String xml = HttpRequests.request(RSS_URL)
                    .connectTimeout(10_000)
                    .readTimeout(10_000)
                    .readString();
            return parseFirstItem(xml);
        } catch (IOException e) {
            return null;
        }
    }

    @Nullable
    private RssItem parseFirstItem(String xml) {
        FirstItemHandler handler = new FirstItemHandler();
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            SAXParser parser = factory.newSAXParser();
            parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), handler);
        } catch (Exception ignored) {
            // StopParsingException (wrapped in SAXException) is expected
            // after the first <item> is fully parsed; other exceptions
            // mean the XML is malformed — either way, return whatever
            // the handler managed to extract.
        }
        return handler.result;
    }

    record RssItem(String title, String link, String pubDate, String guid) {}

    /**
     * SAX handler that extracts only the first {@code <item>} from the RSS feed
     * and stops parsing immediately after.
     */
    private static class FirstItemHandler extends DefaultHandler {
        RssItem result;
        private boolean inItem;
        private String currentTag;
        private final StringBuilder text = new StringBuilder();
        private String title;
        private String link;
        private String pubDate;
        private String guid;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if ("item".equals(qName)) {
                inItem = true;
            }
            if (inItem) {
                currentTag = qName;
                text.setLength(0);
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inItem && currentTag != null) {
                text.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (!inItem) {
                return;
            }
            switch (qName) {
                case "title" -> title = text.toString().trim();
                case "link" -> link = text.toString().trim();
                case "pubDate" -> pubDate = text.toString().trim();
                case "guid" -> guid = text.toString().trim();
                case "item" -> {
                    if (title != null && link != null && guid != null) {
                        result = new RssItem(title, link, pubDate, guid);
                    }
                    throw new StopParsingException();
                }
            }
            currentTag = null;
        }
    }

    private static class StopParsingException extends RuntimeException {}

    // ==================== Notification ====================

    private void showNotification(RssItem item) {
        String localDate = formatLocalDate(item.pubDate());

        Notification notification = NotificationGroupManager.getInstance()
                .getNotificationGroup("JavaFX Tools")
                .createNotification(
                        "[" + localDate + "] JFX Links of the Week!",
                        item.title(),
                        NotificationType.INFORMATION
                );

        notification.addAction(new AnAction(
                FxToolsBundle.message("notification.lotw.action.read")) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                BrowserUtil.browse(item.link());
                notification.expire();
            }
        });

        notification.addAction(new AnAction(
                FxToolsBundle.message("notification.lotw.action.configure")) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                ShowSettingsUtil.getInstance()
                        .showSettingsDialog(null,
                                FxToolsBundle.message("settings.displayName"));
                notification.expire();
            }
        });

        Notifications.Bus.notify(notification);
    }

    @NotNull
    private String formatLocalDate(@Nullable String rfcDate) {
        if (rfcDate == null || rfcDate.isEmpty()) {
            return "";
        }
        try {
            ZonedDateTime gmt = ZonedDateTime.parse(rfcDate,
                    DateTimeFormatter.RFC_1123_DATE_TIME);
            LocalDate local = gmt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
            return String.format("%02d-%02d", local.getMonthValue(), local.getDayOfMonth());
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void dispose() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
    }
}
