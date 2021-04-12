package com.example.demodecoratequeues;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@SpringBootApplication
public class DemoDecorateQueuesApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoDecorateQueuesApplication.class, args);
	}

	@Configuration
	public static class Config {
		@Bean
		WebClient webClient(WebClient.Builder webClientBuilder) {
			return webClientBuilder.baseUrl("https://httpbin.org/").build();
		}

		@Bean
		Service service(WebClient webClient) {
			return new DefaultService(webClient);
		}

//		@Bean
//		Disposable local(Service service) {
//			return service.invoke()
//					.subscribeOn(Schedulers.newParallel("my-subscribe"))
//					.subscribe();
//		}

		@Bean
		Disposable remote(Service service) {
			return service.invokeRemote()
					.subscribeOn(Schedulers.newParallel("my-subscribe"))
					.subscribe();
		}
	}

	public interface Service {
		Flux<String> invoke();
		Flux<String> invokeRemote();
	}

	public static class DefaultService implements Service {
		private static final Logger log = LoggerFactory.getLogger(DefaultService.class);
		private final WebClient webClient;

		public DefaultService(WebClient webClient) {
			this.webClient = webClient;
		}

		@Override
		@NewSpan
		public Flux<String> invoke() {
			return Flux.interval(Duration.ofSeconds(5))
					.map(it -> "Event ----> " + it)
					.doOnNext(it -> log.info("Generated event: {}", it))
					.publishOn(Schedulers.newParallel("my-publish"))
					.doOnNext(it -> log.info("Published event: {}", it))
					.doOnSubscribe(it -> log.info("Subscribing local!"));
		}

		@Override
		@NewSpan
		public Flux<String> invokeRemote() {
			return Flux.interval(Duration.ofSeconds(5))
					.flatMap(it -> webClient.get().uri("get").retrieve().bodyToMono(String.class))
					.doOnNext(it -> log.info("Remote event: {}", it))
					.publishOn(Schedulers.newParallel("my-publish"))
					.doOnNext(it -> log.info("Published event: {}", it))
					.doOnSubscribe(it -> log.info("Subscribing remote!"));
		}
	}
}
