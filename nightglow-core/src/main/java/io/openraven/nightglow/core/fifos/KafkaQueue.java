package io.openraven.nightglow.core.fifos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.openraven.nightglow.api.NGEnvelope;
import io.openraven.nightglow.core.config.ConfigException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class KafkaQueue implements FifoQueue {

  private static final ObjectMapper MAPPER = new ObjectMapper()
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    .findAndRegisterModules();

  private static final Map<String, Object> DEFAULT_PROPERTIES = Map.of(
    ProducerConfig.ACKS_CONFIG, "all",
    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()
  );

  private final Producer<String, String> producer;
  private final String topic;

  public KafkaQueue(Map<String, Object> properties) {
    var props = new HashMap<String, Object>();
    props.putAll(DEFAULT_PROPERTIES);
    props.putAll(properties);

    var t = props.remove("topic");
    if (Objects.isNull(t)) {
      throw new ConfigException("Kafka 'topic' value must be set under properties");
    }
    this.topic = t.toString();
    producer = new KafkaProducer<>(props);
  }

  @Override
  public void add(NGEnvelope env) throws FifoException {
    try {
      producer.send(new ProducerRecord<>(topic, MAPPER.writeValueAsString(env)));
    } catch (JsonProcessingException ex) {
      throw new FifoException("Couldn't serialize envelope", ex);
    }
  }
}