package dev.yeonwoo.chipthrone.alert;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ApplicationReadyAlertListener implements ApplicationListener<ApplicationReadyEvent> {

    private final AlertService alertService;
    private final String version;

    public ApplicationReadyAlertListener(
            AlertService alertService,
            Environment environment,
            org.springframework.beans.factory.ObjectProvider<BuildProperties> buildProperties
    ) {
        this.alertService = alertService;
        BuildProperties build = buildProperties.getIfAvailable();
        this.version = build == null
                ? environment.getProperty("info.app.version", "0.0.1")
                : build.getVersion();
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        alertService.notifyStartup(version);
    }
}
