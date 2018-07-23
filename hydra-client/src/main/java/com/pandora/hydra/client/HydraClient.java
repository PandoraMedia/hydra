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

package com.pandora.hydra.client;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.pandora.hydra.common.TestSuite;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author Justin Guerra
 * @since 8/16/16
 */
public class HydraClient {

    private final HydraApi api;
    private final Configuration config;

    public HydraClient(Configuration configuration) {
        this.config = configuration;
        this.api = createHydraApi(config);
    }

    private HydraApi createHydraApi(Configuration config) {
        String scheme = config.isHttps() ? "https" : "http";
        HttpUrl.Builder urlBuilder = new HttpUrl.Builder().scheme(scheme)
                .host(config.getRemoteHost());

        if (config.getRemotePort() != null) {
            urlBuilder.port(config.getRemotePort());
        }

        OkHttpClient client = new OkHttpClient.Builder().readTimeout(config.getClientTimeout(), TimeUnit.MILLISECONDS).build();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(urlBuilder.build())
                .addConverterFactory(GsonConverterFactory.create(new Gson()))
                .client(client)
                .build();

        return retrofit.create(HydraApi.class);
    }

    /**
     * Posts tests time to hydra server for partitioning purposes
     * @param results test results
     * @throws IOException in case of exceptions
     */
    public void postTestRuntimes(List<TestSuite> results) throws IOException {

        Multimap<String, TestSuite> map = ArrayListMultimap.create();

        for (TestSuite suite : results) {
            map.put(suite.getProject(), suite);
        }

        for (Map.Entry<String, Collection<TestSuite>> entry : map.asMap().entrySet()) {
            String projectName = entry.getKey();
            Response<ResponseBody> response = api.postTestTimes(config.getJobName(), config.getSlaveName(),
                    projectName, entry.getValue()).execute();

            if(response.isSuccessful()) {
                System.out.println("Successfully POSTed test results to hydra server for project " + projectName);
            } else {
                System.out.println("Failed to POST test results to hydra server for project " + projectName + ". " + response.message());
            }
        }
    }

    public Set<String> getExcludes() throws IOException {
        String hostList = String.join(",", config.getHostList());
        Response<Set<String>> response = api.getExcludes(config.getJobName(), config.getSlaveName(), hostList, config.getBuildTag()).execute();

        if(response.isSuccessful()) {
            return response.body();
        } else {
            throw new IllegalStateException("Failed to retrieve tests partitions: " + response.message());
        }
    }

    public Set<List<String>> getThreadPartitions(int numThreads) throws IOException {
        String hostList = String.join(",", config.getHostList());

        Response<Set<List<String>>> response = api.getThreadPartitions(config.getJobName(), config.getSlaveName(),
                hostList, config.getBuildTag(), numThreads).execute();

        if(response.isSuccessful()) {
            return response.body();
        } else {
            throw new IllegalStateException("Failed to retrieve thread partitions: " + response.message());
        }
    }


}
