package cl.duoc.worlddelivery_rabbit_admin.api;
import cl.duoc.worlddelivery_rabbit_admin.api.Requests.*;
import cl.duoc.worlddelivery_rabbit_admin.service.RabbitAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/rabbitmq")
public class RabbitAdminController {
    private final RabbitAdminService service;
    public RabbitAdminController(RabbitAdminService service) { this.service=service; }
    @PostMapping("/queues") @ResponseStatus(HttpStatus.CREATED)
    public QueueRequest createQueue(@Valid @RequestBody QueueRequest request) { return service.createQueue(request); }
    @GetMapping("/queues/{name}")
    public QueueStatus queueStatus(@PathVariable @Pattern(regexp=Requests.NAME) String name) { return service.queueStatus(name); }
    @PostMapping("/queues/{name}/purge")
    public PurgeStatus purgeQueue(@PathVariable @Pattern(regexp=Requests.NAME) String name) { return service.purgeQueue(name); }
    @DeleteMapping("/queues/{name}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQueue(@PathVariable @Pattern(regexp=Requests.NAME) String name) { service.deleteQueue(name); }
    @PostMapping("/exchanges") @ResponseStatus(HttpStatus.CREATED)
    public ExchangeRequest createExchange(@Valid @RequestBody ExchangeRequest request) { return service.createExchange(request); }
    @DeleteMapping("/exchanges/{name}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExchange(@PathVariable @Pattern(regexp=Requests.NAME) String name) { service.deleteExchange(name); }
    @PostMapping("/bindings") @ResponseStatus(HttpStatus.CREATED)
    public BindingRequest createBinding(@Valid @RequestBody BindingRequest request) { return service.createBinding(request); }
    @DeleteMapping("/bindings") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBinding(@RequestParam @Pattern(regexp=Requests.NAME) String queue,
        @RequestParam @Pattern(regexp=Requests.NAME) String exchange,
        @RequestParam @jakarta.validation.constraints.Size(max=128) @Pattern(regexp="[A-Za-z0-9._*#-]*") String routingKey) {
        service.deleteBinding(new BindingRequest(queue,exchange,routingKey));
    }
}
