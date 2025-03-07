package com.bank.dbank.service.impl;

import com.bank.dbank.dto.*;
import com.bank.dbank.entity.User;
import com.bank.dbank.repository.UserRepository;
import com.bank.dbank.utils.AccountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;

@Service
public class UserServiceImpl implements UserService{
  @Autowired
  UserRepository userRepository;

  @Autowired
  EmailService emailService;
  @Autowired
  TransactionService transactionService;
  @Override
  public BankResponse createAccount(UserRequest userRequest) {
    /**
     * Creating an account - saving  a new user in the db
     * Check if already account exist
     */
    if(userRepository.existsByEmail(userRequest.getEmail())){
      return BankResponse.builder()
              .responseCode(AccountUtils.ACCOUNT_EXIST_CODE)
              .responseMessage(AccountUtils.ACCOUNT_EXIST_MESSAGE)
              .accountInfo(null)
              .build();
    }
    User newUser = User.builder()
            .firstName(userRequest.getFirstName())
            .lastName(userRequest.getLastName())
            .otherName(userRequest.getOtherName())
            .gender(userRequest.getGender())
            .address(userRequest.getAddress())
            .stateOfOrigin(userRequest.getStateOfOrigin())
            .accountNumber(AccountUtils.generateAccountNumber())
            .accountBalance(BigDecimal.ZERO)
            .email(userRequest.getEmail())
//            .password(passwordEncoder.encode(userRequest.getPassword()))
            .phoneNumber(userRequest.getPhoneNumber())
//            .alternativePhoneNumber(userRequest.getAlternativePhoneNumber())
            .status("ACTIVE")
            .build();

    User savedUser = userRepository.save(newUser);

    //send email alert
    EmailDetails emailDetails = EmailDetails.builder()
            .recipient(savedUser.getEmail())
            .subject("ACCOUNT CREATION")
            .messageBody("Congratulations! Your Account Has been Successfully Created.\nYour Account Details: \n" +
                    "Account Name: " + savedUser.getFirstName() + " " + savedUser.getLastName() + " " + savedUser.getOtherName() + "\nAccount Number: " + savedUser.getAccountNumber())
            .build();
    emailService.sendEmailAlert(emailDetails);
    return BankResponse.builder()
            .responseCode(AccountUtils.ACCOUNT_CREATION_CODE)
            .responseMessage(AccountUtils.ACCOUNT_CREATION_MESSAGE)
            .accountInfo(AccountInfo.builder()
                    .accountBalance(savedUser.getAccountBalance())
                    .accountNumber(savedUser.getAccountNumber())
                    .accountName(savedUser.getFirstName() + " " + savedUser.getLastName() + " " + savedUser.getOtherName())
                    .build())
            .build();
  }

  @Override
  public BankResponse balanceEnquiry(EnquiryRequest request) {
    //check whether the provided account exist
    boolean isAccountExist = userRepository.existsByAccountNumber(request.getAccountNumber());
    if (!isAccountExist) {
      return BankResponse.builder()
              .responseCode(AccountUtils.ACCOUNT_NOT_EXIST_CODE)
              .responseMessage(AccountUtils.ACCOUNT_NOT_EXIST_MESSAGE)
              .accountInfo(null)
              .build();
    }
    User foundUser = userRepository.findByAccountNumber(request.getAccountNumber());
    return BankResponse.builder()
            .responseCode(AccountUtils.ACCOUNT_FOUND_CODE)
            .responseMessage(AccountUtils.ACCOUNT_FOUND_SUCCESS)
            .accountInfo(AccountInfo.builder()
                    .accountBalance(foundUser.getAccountBalance())
                    .accountNumber(request.getAccountNumber())
                    .accountName(foundUser.getFirstName() + " " + foundUser.getLastName() + " " + foundUser.getOtherName())
                    .build())
            .build();
  }
  @Override
  public String nameEnquiry(EnquiryRequest request) {
    boolean isAccountExist = userRepository.existsByAccountNumber(request.getAccountNumber());
    if (!isAccountExist){
      return AccountUtils.ACCOUNT_NOT_EXIST_MESSAGE;
    }
    User foundUser = userRepository.findByAccountNumber(request.getAccountNumber());
    return foundUser.getFirstName() + " " + foundUser.getLastName() + " " + foundUser.getOtherName();
  }

  @Override
  public BankResponse creditAccount(CreditDebitRequest request) {
    //checking if the account exist
    //checking if the account exists
    boolean isAccountExist = userRepository.existsByAccountNumber(request.getAccountNumber());
    if (!isAccountExist){
      return BankResponse.builder()
              .responseCode(AccountUtils.ACCOUNT_NOT_EXIST_CODE)
              .responseMessage(AccountUtils.ACCOUNT_NOT_EXIST_MESSAGE)
              .accountInfo(null)
              .build();
    }

    User userToCredit = userRepository.findByAccountNumber(request.getAccountNumber());
    userToCredit.setAccountBalance(userToCredit.getAccountBalance().add(request.getAmount()));
    userRepository.save(userToCredit);

    //Save transaction
    TransactionDto transactionDto = TransactionDto.builder()
            .accountNumber(userToCredit.getAccountNumber())
            .transactionType("CREDIT")
            .amount(request.getAmount())
            .build();

    transactionService.saveTransaction(transactionDto);

    return BankResponse.builder()
            .responseCode(AccountUtils.ACCOUNT_CREDITED_SUCCESS)
            .responseMessage(AccountUtils.ACCOUNT_CREDITED_SUCCESS_MESSAGE)
            .accountInfo(AccountInfo.builder()
                    .accountName(userToCredit.getFirstName() + " " + userToCredit.getLastName() + " " + userToCredit.getOtherName())
                    .accountBalance(userToCredit.getAccountBalance())
                    .accountNumber(request.getAccountNumber())
                    .build())
            .build();
  }

  @Override
  public BankResponse debitAccount(CreditDebitRequest request) {
    boolean isAccountExist = userRepository.existsByAccountNumber(request.getAccountNumber());
    if (!isAccountExist){
      return BankResponse.builder()
              .responseCode(AccountUtils.ACCOUNT_NOT_EXIST_CODE)
              .responseMessage(AccountUtils.ACCOUNT_NOT_EXIST_MESSAGE)
              .accountInfo(null)
              .build();
    }

    User userToDebit = userRepository.findByAccountNumber(request.getAccountNumber());
    BigInteger availableBalance =userToDebit.getAccountBalance().toBigInteger();
    BigInteger debitAmount = request.getAmount().toBigInteger();
    if ( availableBalance.intValue() < debitAmount.intValue()){
      return BankResponse.builder()
              .responseCode(AccountUtils.INSUFFICIENT_BALANCE_CODE)
              .responseMessage(AccountUtils.INSUFFICIENT_BALANCE_MESSAGE)
              .accountInfo(null)
              .build();
    }


    else {
      userToDebit.setAccountBalance(userToDebit.getAccountBalance().subtract(request.getAmount()));
      userRepository.save(userToDebit);
      TransactionDto transactionDto = TransactionDto.builder()
              .accountNumber(userToDebit.getAccountNumber())
              .transactionType("CREDIT")
              .amount(request.getAmount())
              .build();

      transactionService.saveTransaction(transactionDto);
      return BankResponse.builder()
              .responseCode(AccountUtils.ACCOUNT_DEBITED_SUCCESS)
              .responseMessage(AccountUtils.ACCOUNT_DEBITED_MESSAGE)
              .accountInfo(AccountInfo.builder()
                      .accountNumber(request.getAccountNumber())
                      .accountName(userToDebit.getFirstName() + " " + userToDebit.getLastName() + " " + userToDebit.getOtherName())
                      .accountBalance(userToDebit.getAccountBalance())
                      .build())
              .build();
    }
  }

  @Override
  public BankResponse transfer(TransferRequest request) {


    boolean isDestinationAccountExist = userRepository.existsByAccountNumber(request.getDestinationAccountNumber());
    if (!isDestinationAccountExist){
      return BankResponse.builder()
              .responseCode(AccountUtils.ACCOUNT_NOT_EXIST_CODE)
              .responseMessage(AccountUtils.ACCOUNT_NOT_EXIST_MESSAGE)
              .accountInfo(null)
              .build();
    }

    User sourceAccountUser = userRepository.findByAccountNumber(request.getSourceAccountNumber());
    if (request.getAmount().compareTo(sourceAccountUser.getAccountBalance()) > 0){
      return BankResponse.builder()
              .responseCode(AccountUtils.INSUFFICIENT_BALANCE_CODE)
              .responseMessage(AccountUtils.INSUFFICIENT_BALANCE_MESSAGE)
              .accountInfo(null)
              .build();
    }

    sourceAccountUser.setAccountBalance(sourceAccountUser.getAccountBalance().subtract(request.getAmount()));
    String sourceUsername = sourceAccountUser.getFirstName() + " " + sourceAccountUser.getLastName() + " " + sourceAccountUser.getOtherName();

    userRepository.save(sourceAccountUser);
    EmailDetails debitAlert = EmailDetails.builder()
            .subject("DEBIT ALERT")
            .recipient(sourceAccountUser.getEmail())
            .messageBody("The sum of " + request.getAmount() + " has been deducted from your account! Your current balance is " + sourceAccountUser.getAccountBalance())
            .build();

    emailService.sendEmailAlert(debitAlert);

    User destinationAccountUser = userRepository.findByAccountNumber(request.getDestinationAccountNumber());
    destinationAccountUser.setAccountBalance(destinationAccountUser.getAccountBalance().add(request.getAmount()));
//        String recipientUsername = destinationAccountUser.getFirstName() + " " + destinationAccountUser.getLastName() + " " + destinationAccountUser.getOtherName();
    userRepository.save(destinationAccountUser);
    EmailDetails creditAlert = EmailDetails.builder()
            .subject("CREDIT ALERT")
            .recipient(sourceAccountUser.getEmail())
            .messageBody("The sum of " + request.getAmount() + " has been sent to your account from " + sourceUsername + " Your current balance is " + sourceAccountUser.getAccountBalance())
            .build();

    emailService.sendEmailAlert(creditAlert);

    TransactionDto transactionDto = TransactionDto.builder()
            .accountNumber(destinationAccountUser.getAccountNumber())
            .transactionType("CREDIT")
            .amount(request.getAmount())
            .build();

    transactionService.saveTransaction(transactionDto);

    return BankResponse.builder()
            .responseCode(AccountUtils.TRANSFER_SUCCESSFUL_CODE)
            .responseMessage(AccountUtils.TRANSFER_SUCCESSFUL_MESSAGE)
            .accountInfo(null)
            .build();
  }
}

