package com.jpmc.midascore.KafkaService;

import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.foundation.Incentive;
import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.repository.TransactionRecordRepository;
import com.jpmc.midascore.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

import java.util.Optional;

@Service
public class KafkaTransactionListener {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRecordRepository transactionRecordRepository;

    @Autowired
    private RestTemplate restTemplate;



    @KafkaListener(topics = "${general.kafka-topic}", groupId = "midas-group", containerFactory = "kafkaListenerContainerFactory")
    public void consume(Transaction transaction) {
        System.out.println("Received: " + transaction);

        Long senderId = transaction.getSenderId();
        Long recipientId = transaction.getRecipientId();
        float amount = transaction.getAmount();

        Optional<UserRecord> senderOpt = userRepository.findById(senderId);
        Optional<UserRecord> recipientOpt = userRepository.findById(recipientId);

        // ✅ Validate sender and recipient existence
        if (senderOpt.isEmpty() || recipientOpt.isEmpty()) {
            System.out.println("❌ Invalid sender or recipient. Discarding transaction.");
            return;
        }

        UserRecord sender = senderOpt.get();
        UserRecord recipient = recipientOpt.get();

        // ✅ Validate sender balance
        if (sender.getBalance() < amount) {
            System.out.println("❌ Insufficient balance. Discarding transaction.");
            return;
        }

        // ✅ Call Incentive API
        Incentive incentiveResponse = restTemplate.postForObject(
                "http://localhost:8080/incentive",
                transaction,
                Incentive.class
        );

        float incentive = (incentiveResponse != null) ? incentiveResponse.getAmount() : 0.0f;
        System.out.println("✅ Incentive received: " + incentive);

        // ✅ Update balances
        sender.setBalance(sender.getBalance() - amount);
        recipient.setBalance(recipient.getBalance() + amount + incentive);

        // ✅ Save updated users
        userRepository.save(sender);
        userRepository.save(recipient);

        // ✅ Save transaction record
        TransactionRecord record = new TransactionRecord();
        record.setSender(sender);
        record.setRecipient(recipient);
        record.setAmount(amount);
        record.setTimestamp(LocalDateTime.now());

        transactionRecordRepository.save(record);

        System.out.println("✅ Transaction processed and recorded with incentive.");
    }


}
