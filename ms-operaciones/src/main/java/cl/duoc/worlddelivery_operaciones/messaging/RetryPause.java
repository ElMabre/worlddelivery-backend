package cl.duoc.worlddelivery_operaciones.messaging;

import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class RetryPause {
    public void await(Duration interval) throws InterruptedException {
        Thread.sleep(interval.toMillis());
    }
}
