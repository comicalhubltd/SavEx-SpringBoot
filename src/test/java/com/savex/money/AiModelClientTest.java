package com.savex.money;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.*;
import org.springframework.http.*;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import reactor.core.publisher.Mono;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class AiModelClientTest {
    final ObjectMapper json=new ObjectMapper();
    @Test void sendsStructuredStoreFalseRequestWithoutToolsAndParsesOnlyCompletedText() throws Exception {
        var settings=new AiSettings();settings.setApiKey("test-fixture-key");
        WebClient.Builder builder=WebClient.builder().exchangeFunction(request->{
            assertThat(request.url().toString()).isEqualTo("https://api.openai.com/v1/responses");
            var output=new MockClientHttpRequest(HttpMethod.POST,request.url());
            request.body().insert(output,new BodyInserter.Context(){
                public List<HttpMessageWriter<?>> messageWriters(){return ExchangeStrategies.withDefaults().messageWriters();}
                public Optional<ServerHttpRequest> serverRequest(){return Optional.empty();}
                public Map<String,Object> hints(){return Map.of();}
            }).block();
            String body=output.getBodyAsString().block();
            assertThat(body).contains("\"store\":false","json_schema","No tools").doesNotContain("\"tools\"","test-fixture-key");
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type","application/json")
                .body("{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"summary\\\":\\\"Pending\\\",\\\"uncertainties\\\":\\\"Missing proof\\\",\\\"nextSteps\\\":\\\"Investigate\\\"}\"}]}]}").build());
        });
        var result=new AiModelClient(settings,json,builder).explain(json.readTree("{\"localStatus\":\"PENDING\"}"),List.of());
        assertThat(result.path("summary").asText()).isEqualTo("Pending");
    }
    @Test void refusesIncompleteRefusalAndMalformedResponses() {
        for(String response:List.of("{\"status\":\"incomplete\"}","{\"status\":\"completed\",\"output\":[]}")) {
            var client=new AiModelClient(new AiSettings(),json,WebClient.builder().exchangeFunction(request->Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type","application/json").body(response).build())));
            assertThatThrownBy(()->client.explain(json.createObjectNode(),List.of())).isInstanceOf(IllegalStateException.class);
        }
    }
}
