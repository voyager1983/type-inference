/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package l3;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Map;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.KeyValueTextInputFormat;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.TextInputFormat;
import org.apache.hadoop.mapred.jobcontrol.Job;
import org.apache.hadoop.mapred.jobcontrol.JobControl;
import org.apache.hadoop.mapred.lib.IdentityMapper;

import com.n1analytics.paillier.EncryptedNumber;
import com.n1analytics.paillier.PaillierContext;
import com.n1analytics.paillier.PaillierPublicKey;

import encryption.Util;

public class L3 {

    public static class ReadPageViews extends MapReduceBase
        implements Mapper<LongWritable, Text, Text, Text> {

        public void map(
                LongWritable k,
                Text val,
                OutputCollector<Text, Text> oc,
                Reporter reporter) throws IOException {

            List<Text> fields = Library.splitLine(val, '');
            // Prepend an index to the value so we know which file
            // it came from.
            Text outVal = new Text("1" + fields.get(6).toString());
            oc.collect(fields.get(0), outVal);
        }
    }

    public static class ReadUsers extends MapReduceBase
        implements Mapper<LongWritable, Text, Text, Text> {

        public void map(
                LongWritable k,
                Text val,
                OutputCollector<Text, Text> oc,
                Reporter reporter) throws IOException {
            List<Text> fields = Library.splitLine(val, '');
            // Prepend an index to the value so we know which file
            // it came from.
            Text outVal = new Text("2");
            oc.collect(fields.get(0), outVal);

        }
    }

    public static class Join extends MapReduceBase
        implements Reducer<Text, Text, Text, Text> {

    	private PaillierContext context;
		private PaillierPublicKey pub;
		private EncryptedNumber zero;
		
		@Override
		public void configure(JobConf conf) {
			String pubKey = conf.get("pubKey");
			pub = new PaillierPublicKey(new BigInteger(pubKey));
			context = pub.createSignedContext();
			zero = context.encrypt(0);
		}
		
        public void reduce(
                Text key,
                Iterator<Text> iter, 
                OutputCollector<Text, Text> oc,
                Reporter reporter) throws IOException {
            // For each value, figure out which file it's from and store it
            // accordingly.
            List<String> first = new ArrayList<String>();
            List<String> second = new ArrayList<String>();

            while (iter.hasNext()) {
                Text t = iter.next();
                String value = t.toString();
                if (value.charAt(0) == '1') first.add(value.substring(1));
                else second.add(value.substring(1));
                reporter.setStatus("OK");
            }

            reporter.setStatus("OK");

            if (first.size() == 0 || second.size() == 0) return;

            // Do the cross product, and calculate the sum
            EncryptedNumber sum = zero;
            for (String s1 : first) {
            	if (s1.isEmpty()) continue;
            	EncryptedNumber s1en = Util.getAHCipher(s1, context);
                for (@SuppressWarnings("unused") String s2 : second) {
                    try {
                        //sum += Double.valueOf(s1);
                    	sum = context.add(sum, s1en);
                    } catch (NumberFormatException nfe) {
                    }
                }
            }
            oc.collect(null, new Text(key.toString() + "" +  Util.getAHString(sum)));
        }
    }

    public static void main(String[] args) throws IOException {

        if (args.length!=4) {
            System.out.println("Parameters: inputDir outputDir parallel pubKey");
            System.exit(1);
        }
        String inputDir = args[0];
        String outputDir = args[1];
        String parallel = args[2];
        JobConf lp = new JobConf(L3.class);
        lp.setJobName("L3 Load Page Views");
        lp.setInputFormat(TextInputFormat.class);
        lp.setOutputKeyClass(Text.class);
        lp.setOutputValueClass(Text.class);
        lp.setMapperClass(ReadPageViews.class);
        Properties props = System.getProperties();
        for (Map.Entry<Object,Object> entry : props.entrySet()) {
            lp.set((String)entry.getKey(), (String)entry.getValue());
        }
        FileInputFormat.addInputPath(lp, new Path(inputDir + "/page_viewsCipher"));
        FileOutputFormat.setOutputPath(lp, new Path(outputDir + "/indexed_pages_3"));
        lp.setNumReduceTasks(0);
        Job loadPages = new Job(lp);

        JobConf lu = new JobConf(L3.class);
        lu.setJobName("L3 Load Users");
        lu.setInputFormat(TextInputFormat.class);
        lu.setOutputKeyClass(Text.class);
        lu.setOutputValueClass(Text.class);
        lu.setMapperClass(ReadUsers.class);
        props = System.getProperties();
        for (Map.Entry<Object,Object> entry : props.entrySet()) {
            lu.set((String)entry.getKey(), (String)entry.getValue());
        }
        FileInputFormat.addInputPath(lu, new Path(inputDir + "/usersCipher"));
        FileOutputFormat.setOutputPath(lu, new Path(outputDir + "/indexed_users_3"));
        lu.setNumReduceTasks(0);
        Job loadUsers = new Job(lu);

        JobConf join = new JobConf(L3.class);
        join.setJobName("L3 Join Users and Pages");
        join.set("pubKey", args[3]);
        join.setInputFormat(KeyValueTextInputFormat.class);
        join.setOutputKeyClass(Text.class);
        join.setOutputValueClass(Text.class);
        join.setMapperClass(IdentityMapper.class);
        join.setReducerClass(Join.class);
        props = System.getProperties();
        for (Map.Entry<Object,Object> entry : props.entrySet()) {
            join.set((String)entry.getKey(), (String)entry.getValue());
        }
        FileInputFormat.addInputPath(join, new Path(outputDir + "/indexed_pages_3"));
        FileInputFormat.addInputPath(join, new Path(outputDir + "/indexed_users_3"));
        FileOutputFormat.setOutputPath(join, new Path(outputDir + "/L3out"));
        join.setNumReduceTasks(Integer.parseInt(parallel));
        Job joinJob = new Job(join);
        joinJob.addDependingJob(loadPages);
        joinJob.addDependingJob(loadUsers);

        JobControl jc = new JobControl("L3 join");
        jc.addJob(loadPages);
        jc.addJob(loadUsers);
        jc.addJob(joinJob);

        new Thread(jc).start();
   
        int i = 0;
        while(!jc.allFinished()){
            ArrayList<Job> failures = jc.getFailedJobs();
            if (failures != null && failures.size() > 0) {
                for (Job failure : failures) {
                    System.err.println(failure.getMessage());
                }
                break;
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {}

            if (i % 10000 == 0) {
                System.out.println("Running jobs");
                ArrayList<Job> running = jc.getRunningJobs();
                if (running != null && running.size() > 0) {
                    for (Job r : running) {
                        System.out.println(r.getJobName());
                    }
                }
                System.out.println("Ready jobs");
                ArrayList<Job> ready = jc.getReadyJobs();
                if (ready != null && ready.size() > 0) {
                    for (Job r : ready) {
                        System.out.println(r.getJobName());
                    }
                }
                System.out.println("Waiting jobs");
                ArrayList<Job> waiting = jc.getWaitingJobs();
                if (waiting != null && waiting.size() > 0) {
                    for (Job r : ready) {
                        System.out.println(r.getJobName());
                    }
                }
                System.out.println("Successful jobs");
                ArrayList<Job> success = jc.getSuccessfulJobs();
                if (success != null && success.size() > 0) {
                    for (Job r : ready) {
                        System.out.println(r.getJobName());
                    }
                }
            }
            i++;
        }
        ArrayList<Job> failures = jc.getFailedJobs();
        if (failures != null && failures.size() > 0) {
            for (Job failure : failures) {
                System.err.println(failure.getMessage());
            }
        }
        jc.stop();
    }

}
