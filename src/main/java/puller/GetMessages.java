package puller;

import http.GetRandomUUID;
import com.google.api.gax.batching.FlowControlSettings;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import java.net.MalformedURLException;
import java.time.Duration;
import java.time.Instant;



public class GetMessages {
	
	
	public static void main(String[] args) throws InterruptedException {
		String projectId = System.getenv("PUBSUB_PROJECT_ID");
		String subscriptionId = System.getenv("PUBSUB_SUBSCRIPTION_ID");
		Long maxmessages = Long.valueOf(System.getenv("PUBSUB_MAX_MESSAGES"));
		Long maxsize = Long.valueOf(System.getenv("PUBSUB_MAX_SIZE"));
		
		int parallelpullcount = Integer.parseInt(System.getenv("PUBSUB_PARALLEL_PULL_COUNT"));
		int executorthreadcount = Integer.parseInt(System.getenv("PUBSUB_EXECUTOR_THREAD_COUNT"));
		
		streamingpull(projectId, subscriptionId,maxmessages,maxsize, parallelpullcount,executorthreadcount);
	}



	public static void streamingpull(String projectId, String subscriptionId
			,Long maxmessages,Long maxsize, int parallelpullcount, int executorthreadcount) {
		
		ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId, subscriptionId);

		// Instantiate an asynchronous message receiver.
		MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
			// Handle incoming message, then ack the received message.
			try {
				Instant local_time = Instant.now();
				Instant message_time = Instant.ofEpochSecond(message.getPublishTime().getSeconds(), message.getPublishTime().getNanos());
				long diffInMilliseconds = Duration.between(message_time, local_time).toMillis();
				
				System.out.println("Id: " + message.getMessageId() + 
						", Data: "+message.getData().toStringUtf8() + 
						", Random UUID: "+ GetRandomUUID.getuuid() +
						", Message time: " + message_time.toString() +
						", Local time: " + local_time.toString() +
						", Difference [ms]: "+ diffInMilliseconds);
				
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			consumer.ack();
		};

		Subscriber subscriber = null;
		try {
			// Provides an executor service for processing messages. The default
			// `executorProvider` used
			// by the subscriber has a default thread count of 5.
			ExecutorProvider executorProvider = InstantiatingExecutorProvider.newBuilder().setExecutorThreadCount(executorthreadcount)
					.build();

			// `setParallelPullCount` determines how many StreamingPull streams the
			// subscriber will open
			// to receive message. It defaults to 1. `setExecutorProvider` configures an
			// executor for the
			// subscriber to process messages. Here, the subscriber is configured to open 2
			// streams for
			// receiving messages, each stream creates a new executor with 4 threads to help
			// process the
			// message callbacks. In total 2x4=8 threads are used for message processing.
			
			FlowControlSettings flowControlSettings = FlowControlSettings.newBuilder()
					// 1,000 outstanding messages. Must be >0. It controls the maximum number of
					// messages
					// the subscriber receives before pausing the message stream.
					.setMaxOutstandingElementCount(maxmessages)
					// 100 MiB. Must be >0. It controls the maximum size of messages the subscriber
					// receives before pausing the message stream.
					.setMaxOutstandingRequestBytes(maxsize * 1024L * 1024L).build();
			
			
			subscriber = Subscriber.newBuilder(subscriptionName, receiver).setParallelPullCount(parallelpullcount)
					.setFlowControlSettings(flowControlSettings)
					.setExecutorProvider(executorProvider).build();
			
			subscriber.addListener(new Subscriber.Listener() {
				public void failed(Subscriber.State from, Throwable failure) {
					System.out.println(failure.getStackTrace());
					if (!executorProvider.getExecutor().isShutdown()) {
						streamingpull(projectId, subscriptionId,maxmessages,maxsize,parallelpullcount,executorthreadcount);
					}
				}
			}, MoreExecutors.directExecutor());

			// Start the subscriber.
			subscriber.startAsync().awaitRunning();
			System.out.printf("Listening for messages on %s:\n", subscriptionName.toString());
			// Allow the subscriber to run for 30s unless an unrecoverable error occurs.
			//subscriber.awaitTerminated(300, TimeUnit.SECONDS);
			subscriber.awaitTerminated();
		} catch (IllegalStateException e) {
			// Shut down the subscriber after 30s. Stop receiving messages.
			subscriber.stopAsync();
			e.printStackTrace();
		}
	}
	
	
}
