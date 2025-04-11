package com.example;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.Schema;
import org.apache.kafka.clients.producer.ProducerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Random;

public class AvroProducer {
    private static final Logger logger = LoggerFactory.getLogger(AvroProducer.class);
    private static final Random random = new Random();

    private static Properties readConfig(String filePath) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = AvroProducer.class.getClassLoader().getResourceAsStream(filePath)) {
            if (input == null) {
                throw new IOException("Property file '" + filePath + "' not found in the classpath");
            }
            properties.load(input);
        }
        return properties;
    }

    private static String generateRandomName() {
        String[] names = {"Alice", "Bob", "Charlie", "David", "Eve", "Frank", "Grace", "Henry", "Ivy", "Jack"};
        return names[random.nextInt(names.length)];
    }

    public static void main(String[] args) {
        KafkaProducer<String, GenericRecord> producer = null;

        try {
            final Properties config = readConfig("client.properties");

            String bootstrapServers = config.getProperty("bootstrap.servers");
            String topic = config.getProperty("topic");

            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
            props.put(ProducerConfig.ACKS_CONFIG, config.getProperty("acks"));
            props.put("schema.registry.url", config.getProperty("schema.registry.url"));

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

            // Create Kafka Producer
            producer = new KafkaProducer<>(props);

            // Define Avro schema
            String schemaString = "{\"type\":\"record\",\"name\":\"Users\",\"fields\":[{\"name\":\"id\",\"type\":\"int\"},{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"age\",\"type\":\"int\"}]}";
            Schema avroSchema = new Schema.Parser().parse(schemaString);

            while (true) {
                // Generate random data
                int id = random.nextInt(1000);
                String name = generateRandomName();
                int age = random.nextInt(80) + 18; // Age between 18 and 97

                // Create Avro record
                GenericRecord user = new GenericData.Record(avroSchema);
                user.put("id", id);
                user.put("name", name);
                user.put("age", age);

                ProducerRecord<String, GenericRecord> record = new ProducerRecord<>(topic, user);
                producer.send(record, new Callback() {
                    @Override
                    public void onCompletion(RecordMetadata metadata, Exception exception) {
                        if (exception != null) {
                            logger.error("Error sending Avro message", exception);
                        } else {
                            logger.info("Sent Avro message to topic {} partition {} offset {}", metadata.topic(), metadata.partition(), metadata.offset());
                        }
                    }
                });
                try {
                    Thread.sleep(1000); // Send message every 1 second
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Thread interrupted during sleep", e);
                }
            }
        } catch (IOException e) {
            logger.error("Error reading configuration", e);
        } finally {
            if (producer != null) {
                producer.close();
            }
        }
    }
}