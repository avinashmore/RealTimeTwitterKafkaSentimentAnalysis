package edu.sjsu.cs185c;

import com.google.common.io.Resources;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;

import java.util.Arrays;

import java.util.Properties;
import java.util.Random;
import java.util.Scanner;

public class SentimentAnalyzer {
	public static void main(String[] args) throws IOException {
		// error-check the command line
		if (args.length != 2) {
			System.err.println("usage: Consumer <topic> <threshold> <file>");
			System.exit(1);
		}
		// parse the command-line
		String topic = args[0];

		// setup consumer
		KafkaConsumer<String, String> consumer;
		try (InputStream consumerProps = Resources.getResource("consumer.props").openStream()) {
			Properties consumerProperties = new Properties();
			consumerProperties.load(consumerProps);
			if (consumerProperties.getProperty("group.id") == null) {
				consumerProperties.setProperty("group.id", "group-" + new Random().nextInt(100000));
			}
			consumer = new KafkaConsumer<>(consumerProperties);
			consumer.subscribe(Arrays.asList(topic));
			int timeouts = 0;
			while (true) {
				ConsumerRecords<String, String> records = consumer.poll(200);
				if (records.count() == 0) {
					timeouts++;
				} else {
					System.out.printf("Got %d records after %d timeouts\n", records.count(), timeouts);
					timeouts = 0;
				}
				for (ConsumerRecord<String, String> record : records) {

					String tweetRecord = record.value();
					String[] tokens = tweetRecord.split(",;,");
					String tweet = tokens[1];
					JSONObject responseObject = null;
					JSONParser parser = new JSONParser();
					
					
					// call the restAPI to get the sentiment of the message.
					// Please set the API_KEY
					InputStream input = new URL("http://access.alchemyapi.com/calls/text/TextGetTextSentiment?"
							+ "outputMode=json&apikey=API_KEY&text=" + URLEncoder.encode(tweet, "UTF-8")).openStream();

					Scanner scanner = new Scanner(input);
					String responseBody = scanner.useDelimiter("\\A").next();
					scanner.close();
					responseObject = (JSONObject) parser.parse(responseBody);

					String sentiment;
					if (responseObject.get("status").toString().equals("ERROR")) {
						sentiment = "error";
						System.out.println(responseObject.toString());
						continue;
					} else {
						sentiment = (String) ((JSONObject) responseObject.get("docSentiment")).get("type");
					}

					// Block: publish the tweet with sentiment
					KafkaProducer<String, String> producer = null;
					try (InputStream props = Resources.getResource("producer.props").openStream()) {
						Properties properties = new Properties();
						properties.load(props);
						producer = new KafkaProducer<>(properties);
						String message = tweetRecord + ",;," + sentiment;
						System.out.println(message);
						ProducerRecord<String, String> rec = new ProducerRecord<String, String>(args[1], message);
						// TODO: publish message to topic
						producer.send(rec);
						// TODO: flush producer
						producer.flush();

					} catch (Exception e) {
						System.out.println(e.toString());
					} // endBlock: publish the tweet with sentiment

				}
			}
		} catch (org.json.simple.parser.ParseException e) {

			e.printStackTrace();
		}

	}
}
