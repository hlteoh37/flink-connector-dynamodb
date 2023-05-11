/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.connector.kinesis.util;

import org.apache.flink.annotation.Internal;
import org.apache.flink.connector.aws.config.AWSConfigConstants;
import org.apache.flink.connector.aws.util.AWSGeneralUtil;
import org.apache.flink.connector.kinesis.source.config.SourceConfigConstants;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.apache.flink.connector.kinesis.source.config.SourceConfigConstants.DEFAULT_STREAM_TIMESTAMP_DATE_FORMAT;
import static org.apache.flink.connector.kinesis.source.config.SourceConfigConstants.STREAM_INITIAL_TIMESTAMP;
import static org.apache.flink.connector.kinesis.source.config.SourceConfigConstants.STREAM_TIMESTAMP_DATE_FORMAT;
import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/** Utilities for Flink Kinesis connector configuration. */
@Internal
public class KinesisConfigUtil {
    /**
     * Validate configuration properties for {@link
     * org.apache.flink.connector.kinesis.source.KinesisStreamsSource}.
     */
    public static void validateStreamSourceConfiguration(Properties config) {
        validateStreamSourceConfiguration(config, Collections.emptyList());
    }

    /**
     * Validate configuration properties for {@link
     * org.apache.flink.connector.kinesis.source.KinesisStreamsSource}.
     */
    public static void validateStreamSourceConfiguration(Properties config, List<String> streams) {
        checkNotNull(config, "config can not be null");

        validateAwsConfiguration(config);

        SourceConfigConstants.RecordPublisherType recordPublisherType =
                validateRecordPublisherType(config);

        if (recordPublisherType == SourceConfigConstants.RecordPublisherType.EFO) {
            validateEfoConfiguration(config, streams);
        }

        if (!(config.containsKey(AWSConfigConstants.AWS_REGION)
                || config.containsKey(AWSConfigConstants.AWS_ENDPOINT))) {
            // per validation in AwsClientBuilder
            throw new IllegalArgumentException(
                    String.format(
                            "For KinesisStreamsSource AWS region ('%s') and/or AWS endpoint ('%s') must be set in the config.",
                            AWSConfigConstants.AWS_REGION, AWSConfigConstants.AWS_ENDPOINT));
        }

        if (config.containsKey(SourceConfigConstants.STREAM_INITIAL_POSITION)) {
            String initPosType = config.getProperty(SourceConfigConstants.STREAM_INITIAL_POSITION);

            // specified initial position in stream must be either LATEST, TRIM_HORIZON or
            // AT_TIMESTAMP
            try {
                SourceConfigConstants.InitialPosition.valueOf(initPosType);
            } catch (IllegalArgumentException e) {
                String errorMessage =
                        Arrays.stream(SourceConfigConstants.InitialPosition.values())
                                .map(Enum::name)
                                .collect(Collectors.joining(", "));
                throw new IllegalArgumentException(
                        "Invalid initial position in stream set in config. Valid values are: "
                                + errorMessage);
            }

            // specified initial timestamp in stream when using AT_TIMESTAMP
            if (SourceConfigConstants.InitialPosition.valueOf(initPosType)
                    == SourceConfigConstants.InitialPosition.AT_TIMESTAMP) {
                if (!config.containsKey(STREAM_INITIAL_TIMESTAMP)) {
                    throw new IllegalArgumentException(
                            "Please set value for initial timestamp ('"
                                    + STREAM_INITIAL_TIMESTAMP
                                    + "') when using AT_TIMESTAMP initial position.");
                }
                validateOptionalDateProperty(
                        config,
                        STREAM_INITIAL_TIMESTAMP,
                        config.getProperty(
                                STREAM_TIMESTAMP_DATE_FORMAT, DEFAULT_STREAM_TIMESTAMP_DATE_FORMAT),
                        "Invalid value given for initial timestamp for AT_TIMESTAMP initial position in stream. "
                                + "Must be a valid format: yyyy-MM-dd'T'HH:mm:ss.SSSXXX or non-negative double value. For example, 2016-04-04T19:58:46.480-00:00 or 1459799926.480 .");
            }
        }
        validateOptionalPositiveIntProperty(
                config,
                SourceConfigConstants.SHARD_GETRECORDS_MAX,
                "Invalid value given for maximum records per getRecords shard operation. Must be a valid non-negative integer value.");

        validateOptionalPositiveIntProperty(
                config,
                SourceConfigConstants.SHARD_GETRECORDS_RETRIES,
                "Invalid value given for maximum retry attempts for getRecords shard operation. Must be a valid non-negative integer value.");

        validateOptionalPositiveLongProperty(
                config,
                SourceConfigConstants.SHARD_GETRECORDS_BACKOFF_BASE,
                "Invalid value given for get records operation base backoff milliseconds. Must be a valid non-negative long value.");

        validateOptionalPositiveLongProperty(
                config,
                SourceConfigConstants.SHARD_GETRECORDS_BACKOFF_MAX,
                "Invalid value given for get records operation max backoff milliseconds. Must be a valid non-negative long value.");

        validateOptionalPositiveDoubleProperty(
                config,
                SourceConfigConstants.SHARD_GETRECORDS_BACKOFF_EXPONENTIAL_CONSTANT,
                "Invalid value given for get records operation backoff exponential constant. Must be a valid non-negative double value.");

        validateOptionalPositiveLongProperty(
                config,
                SourceConfigConstants.SHARD_GETRECORDS_INTERVAL_MILLIS,
                "Invalid value given for getRecords sleep interval in milliseconds. Must be a valid non-negative long value.");

        validateOptionalPositiveIntProperty(
                config,
                SourceConfigConstants.SHARD_GETITERATOR_RETRIES,
                "Invalid value given for maximum retry attempts for getShardIterator shard operation. Must be a valid non-negative integer value.");

        validateOptionalPositiveLongProperty(
                config,
                SourceConfigConstants.SHARD_GETITERATOR_BACKOFF_BASE,
                "Invalid value given for get shard iterator operation base backoff milliseconds. Must be a valid non-negative long value.");

        validateOptionalPositiveLongProperty(
                config,
                SourceConfigConstants.SHARD_GETITERATOR_BACKOFF_MAX,
                "Invalid value given for get shard iterator operation max backoff milliseconds. Must be a valid non-negative long value.");

        validateOptionalPositiveDoubleProperty(
                config,
                SourceConfigConstants.SHARD_GETITERATOR_BACKOFF_EXPONENTIAL_CONSTANT,
                "Invalid value given for get shard iterator operation backoff exponential constant. Must be a valid non-negative double value.");

        validateOptionalPositiveLongProperty(
                config,
                SourceConfigConstants.SHARD_DISCOVERY_INTERVAL_MILLIS,
                "Invalid value given for shard discovery sleep interval in milliseconds. Must be a valid non-negative long value.");

        validateOptionalPositiveLongProperty(
                config,
                SourceConfigConstants.LIST_SHARDS_BACKOFF_BASE,
                "Invalid value given for list shards operation base backoff milliseconds. Must be a valid non-negative long value.");

        validateOptionalPositiveLongProperty(
                config,
                SourceConfigConstants.LIST_SHARDS_BACKOFF_MAX,
                "Invalid value given for list shards operation max backoff milliseconds. Must be a valid non-negative long value.");

        validateOptionalPositiveDoubleProperty(
                config,
                SourceConfigConstants.LIST_SHARDS_BACKOFF_EXPONENTIAL_CONSTANT,
                "Invalid value given for list shards operation backoff exponential constant. Must be a valid non-negative double value.");

        validateOptionalPositiveIntProperty(
                config,
                SourceConfigConstants.DESCRIBE_STREAM_CONSUMER_RETRIES,
                "Invalid value given for maximum retry attempts for describe stream consumer operation. Must be a valid non-negative int value.");

        validateOptionalPositiveLongProperty(
                config,
                SourceConfigConstants.DESCRIBE_STREAM_CONSUMER_BACKOFF_MAX,
                "Invalid value given for describe stream consumer operation max backoff milliseconds. Must be a valid non-negative long value.");

        validateOptionalPositiveDoubleProperty(
                config,
                SourceConfigConstants.DESCRIBE_STREAM_CONSUMER_BACKOFF_EXPONENTIAL_CONSTANT,
                "Invalid value given for describe stream consumer operation backoff exponential constant. Must be a valid non-negative double value.");

        validateOptionalPositiveLongProperty(
                config,
                SourceConfigConstants.DESCRIBE_STREAM_CONSUMER_BACKOFF_BASE,
                "Invalid value given for describe stream consumer operation base backoff milliseconds. Must be a valid non-negative long value.");

        validateOptionalPositiveIntProperty(
                config,
                SourceConfigConstants.REGISTER_STREAM_CONSUMER_RETRIES,
                "Invalid value given for maximum retry attempts for register stream operation. Must be a valid non-negative integer value.");

        validateOptionalPositiveIntProperty(
                config,
                SourceConfigConstants.REGISTER_STREAM_CONSUMER_TIMEOUT_SECONDS,
                "Invalid value given for maximum timeout for register stream consumer. Must be a valid non-negative integer value.");

        validateOptionalPositiveLongProperty(
                config,
                SourceConfigConstants.REGISTER_STREAM_CONSUMER_BACKOFF_MAX,
                "Invalid value given for register stream operation max backoff milliseconds. Must be a valid non-negative long value.");

        validateOptionalPositiveLongProperty(
                config,
                SourceConfigConstants.REGISTER_STREAM_CONSUMER_BACKOFF_BASE,
                "Invalid value given for register stream operation base backoff milliseconds. Must be a valid non-negative long value.");

        validateOptionalPositiveDoubleProperty(
                config,
                SourceConfigConstants.REGISTER_STREAM_BACKOFF_EXPONENTIAL_CONSTANT,
                "Invalid value given for register stream operation backoff exponential constant. Must be a valid non-negative double value.");

        validateOptionalPositiveIntProperty(
                config,
                SourceConfigConstants.DEREGISTER_STREAM_CONSUMER_RETRIES,
                "Invalid value given for maximum retry attempts for deregister stream operation. Must be a valid non-negative integer value.");

        validateOptionalPositiveIntProperty(
                config,
                SourceConfigConstants.DEREGISTER_STREAM_CONSUMER_TIMEOUT_SECONDS,
                "Invalid value given for maximum timeout for deregister stream consumer. Must be a valid non-negative integer value.");

        validateOptionalPositiveLongProperty(
                config,
                SourceConfigConstants.DEREGISTER_STREAM_BACKOFF_BASE,
                "Invalid value given for deregister stream operation base backoff milliseconds. Must be a valid non-negative long value.");

        validateOptionalPositiveLongProperty(
                config,
                SourceConfigConstants.DEREGISTER_STREAM_CONSUMER_BACKOFF_MAX,
                "Invalid value given for deregister stream operation max backoff milliseconds. Must be a valid non-negative long value.");

        validateOptionalPositiveDoubleProperty(
                config,
                SourceConfigConstants.DEREGISTER_STREAM_BACKOFF_EXPONENTIAL_CONSTANT,
                "Invalid value given for deregister stream operation backoff exponential constant. Must be a valid non-negative double value.");

        validateOptionalPositiveIntProperty(
                config,
                SourceConfigConstants.SUBSCRIBE_TO_SHARD_RETRIES,
                "Invalid value given for maximum retry attempts for subscribe to shard operation. Must be a valid non-negative integer value.");

        validateOptionalPositiveLongProperty(
                config,
                SourceConfigConstants.SUBSCRIBE_TO_SHARD_BACKOFF_BASE,
                "Invalid value given for subscribe to shard operation base backoff milliseconds. Must be a valid non-negative long value.");

        validateOptionalPositiveLongProperty(
                config,
                SourceConfigConstants.SUBSCRIBE_TO_SHARD_BACKOFF_MAX,
                "Invalid value given for subscribe to shard operation max backoff milliseconds. Must be a valid non-negative long value.");

        validateOptionalPositiveDoubleProperty(
                config,
                SourceConfigConstants.SUBSCRIBE_TO_SHARD_BACKOFF_EXPONENTIAL_CONSTANT,
                "Invalid value given for subscribe to shard operation backoff exponential constant. Must be a valid non-negative double value.");

        if (config.containsKey(SourceConfigConstants.SHARD_GETRECORDS_INTERVAL_MILLIS)) {
            checkArgument(
                    Long.parseLong(
                                    config.getProperty(
                                            SourceConfigConstants.SHARD_GETRECORDS_INTERVAL_MILLIS))
                            < SourceConfigConstants.MAX_SHARD_GETRECORDS_INTERVAL_MILLIS,
                    "Invalid value given for getRecords sleep interval in milliseconds. Must be lower than "
                            + SourceConfigConstants.MAX_SHARD_GETRECORDS_INTERVAL_MILLIS
                            + " milliseconds.");
        }

        validateOptionalPositiveIntProperty(
                config,
                SourceConfigConstants.EFO_HTTP_CLIENT_MAX_CONCURRENCY,
                "Invalid value given for EFO HTTP client max concurrency. Must be positive.");
    }

    /**
     * Validate the record publisher type.
     *
     * @param config config properties
     * @return if {@code SourceConfigConstants.RECORD_PUBLISHER_TYPE} is set, return the parsed
     *     record publisher type. Else return polling record publisher type.
     */
    public static SourceConfigConstants.RecordPublisherType validateRecordPublisherType(
            Properties config) {
        if (config.containsKey(SourceConfigConstants.RECORD_PUBLISHER_TYPE)) {
            String recordPublisherType =
                    config.getProperty(SourceConfigConstants.RECORD_PUBLISHER_TYPE);

            // specified record publisher type in stream must be either EFO or POLLING
            try {
                return SourceConfigConstants.RecordPublisherType.valueOf(recordPublisherType);
            } catch (IllegalArgumentException e) {
                String errorMessage =
                        Arrays.stream(SourceConfigConstants.RecordPublisherType.values())
                                .map(Enum::name)
                                .collect(Collectors.joining(", "));
                throw new IllegalArgumentException(
                        "Invalid record publisher type in stream set in config. Valid values are: "
                                + errorMessage);
            }
        } else {
            return SourceConfigConstants.RecordPublisherType.POLLING;
        }
    }

    /**
     * Validate if the given config is a valid EFO configuration.
     *
     * @param config config properties.
     * @param streams the streams which is sent to match the EFO consumer arn if the EFO
     *     registration mode is set to `NONE`.
     */
    public static void validateEfoConfiguration(Properties config, List<String> streams) {
        SourceConfigConstants.EFORegistrationType efoRegistrationType;
        if (config.containsKey(SourceConfigConstants.EFO_REGISTRATION_TYPE)) {
            String typeInString = config.getProperty(SourceConfigConstants.EFO_REGISTRATION_TYPE);
            // specified efo registration type in stream must be either LAZY, EAGER or NONE.
            try {
                efoRegistrationType =
                        SourceConfigConstants.EFORegistrationType.valueOf(typeInString);
            } catch (IllegalArgumentException e) {
                String errorMessage =
                        Arrays.stream(SourceConfigConstants.EFORegistrationType.values())
                                .map(Enum::name)
                                .collect(Collectors.joining(", "));
                throw new IllegalArgumentException(
                        "Invalid efo registration type in stream set in config. Valid values are: "
                                + errorMessage);
            }
        } else {
            efoRegistrationType = SourceConfigConstants.EFORegistrationType.LAZY;
        }
        if (efoRegistrationType == SourceConfigConstants.EFORegistrationType.NONE) {
            // if the registration type is NONE, then for each stream there must be an according
            // consumer ARN
            List<String> missingConsumerArnKeys = new ArrayList<>();
            for (String stream : streams) {
                String efoConsumerARNKey =
                        SourceConfigConstants.EFO_CONSUMER_ARN_PREFIX + "." + stream;
                if (!config.containsKey(efoConsumerARNKey)) {
                    missingConsumerArnKeys.add(efoConsumerARNKey);
                }
            }
            if (!missingConsumerArnKeys.isEmpty()) {
                String errorMessage =
                        Arrays.stream(missingConsumerArnKeys.toArray())
                                .map(Object::toString)
                                .collect(Collectors.joining(", "));
                throw new IllegalArgumentException(
                        "Invalid efo consumer arn settings for not providing consumer arns: "
                                + errorMessage);
            }
        } else {
            // if the registration type is LAZY or EAGER, then user must provide a self-defined
            // consumer name.
            if (!config.containsKey(SourceConfigConstants.EFO_CONSUMER_NAME)) {
                throw new IllegalArgumentException(
                        "No valid enhanced fan-out consumer name is set through "
                                + SourceConfigConstants.EFO_CONSUMER_NAME);
            }
        }
    }

    /** Validate configuration properties related to Amazon AWS service. */
    public static void validateAwsConfiguration(Properties config) {
        AWSGeneralUtil.validateAwsConfiguration(config);
    }

    private static void validateOptionalPositiveLongProperty(
            Properties config, String key, String message) {
        if (config.containsKey(key)) {
            try {
                long value = Long.parseLong(config.getProperty(key));
                if (value < 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(message);
            }
        }
    }

    private static void validateOptionalPositiveIntProperty(
            Properties config, String key, String message) {
        if (config.containsKey(key)) {
            try {
                int value = Integer.parseInt(config.getProperty(key));
                if (value < 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(message);
            }
        }
    }

    private static void validateOptionalPositiveDoubleProperty(
            Properties config, String key, String message) {
        if (config.containsKey(key)) {
            try {
                double value = Double.parseDouble(config.getProperty(key));
                if (value < 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(message);
            }
        }
    }

    private static void validateOptionalDateProperty(
            Properties config, String timestampKey, String format, String message) {
        if (config.containsKey(timestampKey)) {
            try {
                SimpleDateFormat customDateFormat = new SimpleDateFormat(format);
                customDateFormat.parse(config.getProperty(timestampKey));
            } catch (IllegalArgumentException | NullPointerException exception) {
                throw new IllegalArgumentException(message);
            } catch (ParseException exception) {
                try {
                    double value = Double.parseDouble(config.getProperty(timestampKey));
                    if (value < 0) {
                        throw new IllegalArgumentException(message);
                    }
                } catch (NumberFormatException numberFormatException) {
                    throw new IllegalArgumentException(message);
                }
            }
        }
    }
}
