// ============================================================================
//
// Copyright (C) 2006-2017 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.components.kafka.runtime;

import org.apache.avro.Schema;
import org.apache.avro.generic.IndexedRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.Before;
import org.junit.Test;
import org.talend.components.kafka.dataset.KafkaDatasetProperties;
import org.talend.daikon.java8.Consumer;

import java.util.*;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.Matchers.hasItems;
import static org.junit.Assert.*;
import static org.talend.components.kafka.runtime.KafkaTestConstants.*;

// Same tests as in KafkaDatasetTestIT except that we use a specific field delimiter
public class KafkaDatasetOtherDelimTestIT {

    public final static String fieldDelimiter = "=";

    @Before
    public void init() throws TimeoutException {
        // there may exists other topics than these build in(configured in pom.xml) topics, but ignore them

        // ----------------- Send sample data to TOPIC_IN start --------------------
        String testID = "sampleTest" + new Random().nextInt();

        List<Person> expectedPersons = Person.genRandomList(testID, 10);

        Properties props = new Properties();
        props.put("bootstrap.servers", BOOTSTRAP_HOST);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        Producer<Void, String> producer = new KafkaProducer<>(props);
        for (Person person : expectedPersons) {
            ProducerRecord<Void, String> message = new ProducerRecord<>(TOPIC_IN, person.toCSV(fieldDelimiter));
            producer.send(message);
        }
        producer.close();
        // ----------------- Send sample data to TOPIC_IN end --------------------
    }

    @Test
    public void listTopicForRuntime() throws Exception {
        KafkaDatasetRuntime runtime = new KafkaDatasetRuntime();
        runtime.initialize(null, createDataset(createDatastore()));
        Set<String> topics = runtime.listTopic();
        assertThat(topics, hasItems(TOPIC_IN, TOPIC_OUT));
    }

    @Test
    public void listTopicForProperties() throws Exception {
        KafkaDatasetProperties datasetProperties = createDataset(createDatastore());
        datasetProperties.beforeTopic();
        List<String> possibleTopics = (List<String>) datasetProperties.topic.getPossibleValues();
        assertThat(possibleTopics, hasItems(TOPIC_IN, TOPIC_OUT));
    }

    @Test
    public void getSampleTest() {
        KafkaDatasetRuntime runtime = new KafkaDatasetRuntime();
        runtime.initialize(null, createDatasetCSV(createDatastore(), TOPIC_IN, KafkaDatasetProperties.FieldDelimiterType.OTHER, fieldDelimiter));
        final List<String> actual = new ArrayList<>();
        runtime.getSample(10, new Consumer<IndexedRecord>() {

            @Override
            public void accept(IndexedRecord indexedRecord) {
                actual.add(indexedRecord.toString());
            }
        });
        assertEquals(10, actual.size());
    }

    @Test
    public void getSchemaTest() {
        KafkaDatasetRuntime runtime = new KafkaDatasetRuntime();
        runtime.initialize(null, createDatasetCSV(createDatastore(), TOPIC_IN, KafkaDatasetProperties.FieldDelimiterType.OTHER,fieldDelimiter));
        Schema schema = runtime.getSchema();
        assertNotNull(schema);
        assertNotEquals(0, schema.getFields().size());
        runtime.initialize(null, createDatasetCSV(createDatastore(), "fake", KafkaDatasetProperties.FieldDelimiterType.OTHER,fieldDelimiter));
        schema = runtime.getSchema();
        assertNotNull(schema);
        assertEquals(0, schema.getFields().size());
    }

}
