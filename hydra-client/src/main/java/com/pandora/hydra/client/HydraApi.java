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

import com.pandora.hydra.common.TestSuite;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author Justin Guerra
 * @since 3/15/18
 */
public interface HydraApi {

    @GET("/tests/{jobName}/{hostName}/excludes")
    Call<Set<String>> getExcludes(@Path("jobName") String jobName, @Path("hostName") String hostName,
                                  @Query("host_list") String hostList, @Query("build_tag") String buildTag);

    @GET("/tests/{jobName}/{hostName}/{projectName}/excludes")
    Call<Set<String>> getExcludes(@Path("jobName") String jobName, @Path("hostName") String hostName, @Path("projectName") String projectName,
                                  @Query("host_list") String hostList, @Query("build_tag") String buildTag);

    @GET("/tests/{jobName}/{hostName}/threads")
    Call<Set<List<String>>> getThreadPartitions(@Path("jobName") String jobName, @Path("hostName") String hostName,
                                                @Query("host_list") String hostList, @Query("build_tag") String buildTag,
                                                @Query("num_threads") Integer numThreads);

    @POST("/tests/{jobName}/{hostName}/{project}/runtimes")
    Call<ResponseBody> postTestTimes(@Path("jobName") String jobName, @Path("hostName") String hostName,
                                     @Path("project") String projectName, @Body Collection<TestSuite> tests);

}
