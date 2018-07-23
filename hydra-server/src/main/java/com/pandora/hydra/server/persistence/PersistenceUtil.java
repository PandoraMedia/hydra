/*
 * Copyright Pandora Media Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.pandora.hydra.server.persistence;

import com.pandora.hydra.server.persistence.model.TestTime;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Justin Guerra
 * @since 4/27/18
 */
public class PersistenceUtil {

    static Set<TestTime> findObsoleteTests(Collection<TestTime> testTimes) {
        Optional<Instant> maybeAverage = calculateAverageLastUpdatedTime(testTimes);
        if(!maybeAverage.isPresent()) {
            return Collections.emptySet();
        }

        Instant averageLastUpdated = maybeAverage.get();

        return testTimes.stream()
                .filter(t -> isTestOlderThanAverage(averageLastUpdated, t))
                .collect(Collectors.toSet());

    }

    static Optional<Instant> calculateAverageLastUpdatedTime(Collection<TestTime> testTimes) {
        OptionalDouble optionalAverage = testTimes.stream()
                .map(TestTime::getLastUpdated)
                .filter(Objects::nonNull)
                .mapToLong(Timestamp::getTime)
                .average();

        return optionalAverage.isPresent() ? Optional.of(Instant.ofEpochMilli((long)optionalAverage.getAsDouble())) : Optional.empty();
    }


    static boolean isTestOlderThanAverage(Instant averageInstant, TestTime test) {
        if(test.getLastUpdated() == null) {
            return true;
        }
        Instant lastUpdated = test.getLastUpdated().toInstant();
        return Duration.between(averageInstant, lastUpdated).toDays() < 0;
    }
}
