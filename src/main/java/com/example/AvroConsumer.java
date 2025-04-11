package com.example;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.avro.generic.GenericRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class AvroConsumer {

    private static final Logger logger = LoggerFactory.getLogger(AvroConsumer.class);

    private static Properties readConfig(String filePath) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = AvroConsumer.class.getClassLoader().getResourceAsStream(filePath)) {
            if (input == null) {
                throw new IOException("Property file '" + filePath + "' not found in the classpath");
            }
            properties.load(input);
        }
        return properties;
    }

    public static void main(String[] args) {
        try {
            final Properties config = readConfig("consumer.properties");

            String bootstrapServers = config.getProperty("bootstrap.servers");
            String topic = config.getProperty("topic");
            String groupId = config.getProperty("group.id");
            String schemaRegistryUrl = config.getProperty("schema.registry.url");

            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
            props.put("schema.registry.url", schemaRegistryUrl);
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // Or "latest" depending on your needs

            // Security props
            props.put("security.protocol", config.getProperty("security.protocol"));
            props.put("sasl.mechanism", config.getProperty("sasl.mechanism"));
            props.put("sasl.kerberos.service.name", config.getProperty("sasl.kerberos.service.name"));
            props.put("sasl.kerberos.keytab", "/etc/security/kafka_client1.keytab");
            props.put("sasl.kerberos.principal", "kafka-client/client1.example.com");
            props.put("ssl.keystore.location", config.getProperty("ssl.keystore.location"));
            props.put("ssl.keystore.password", config.getProperty("ssl.keystore.password"));
            props.put("ssl.key.password", config.getProperty("ssl.key.password"));
            props.put("ssl.truststore.location", config.getProperty("ssl.truststore.location"));
            props.put("ssl.truststore.password", config.getProperty("ssl.truststore.password"));
            props.put("ssl.endpoint.identification.algorithm", config.getProperty("ssl.endpoint.identification.algorithm", ""));
            props.put("sasl.jaas.config", config.getProperty("sasl.jaas.config"));

            try (KafkaConsumer<String, GenericRecord> consumer = new KafkaConsumer<>(props)) {
                consumer.subscribe(Collections.singletonList(topic));

                while (true) {
                    ConsumerRecords<String, GenericRecord> records = consumer.poll(Duration.ofMillis(100));

                    for (ConsumerRecord<String, GenericRecord> record : records) {
                        GenericRecord user = record.value();
                        if (user != null) {
                            logger.info("Received User: id={}, name={}, age={}, partition={}, offset={}",
                                    user.get("id"), user.get("name"), user.get("age"), record.partition(), record.offset());
                        } else {
                            logger.warn("Received a null record value.");
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error reading configuration", e);
        }
    }
}