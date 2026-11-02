package cl.duoc.worlddelivery_rabbit_admin.config;
import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
@Configuration
@EnableConfigurationProperties(AdminProperties.class)
public class RabbitConfiguration {
    @Bean RabbitAdmin rabbitAdmin(ConnectionFactory connection) {
        var admin=new RabbitAdmin(connection);
        admin.setAutoStartup(false);
        admin.setIgnoreDeclarationExceptions(false);
        admin.setRedeclareManualDeclarations(false);
        return admin;
    }
}
