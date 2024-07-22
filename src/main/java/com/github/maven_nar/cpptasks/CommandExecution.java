/*
 * #%L
 * Native ARchive plugin for Maven
 * %%
 * Copyright (C) 2002 - 2014 NAR Maven Plugin developers.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.github.maven_nar.cpptasks;


import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Environment;

import java.io.*;
import java.nio.file.Path;
import java.util.Vector;


class StreamGobbler extends Thread {
    InputStream is;
    CCTask task;


    StreamGobbler(InputStream is, CCTask task) {
        this.is = is;
        this.task = task;
    }


    public void run() {
        try {
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null)
                task.log(line);

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }


    }
}


public class CommandExecution {




    public static int runCommand(String[] cmdArgs, Path workDir, CCTask task, Vector<Environment.Variable> env) throws  IOException{


        try {

            //Create ProcessBuilder with the command arguments
            ProcessBuilder pb = new ProcessBuilder(cmdArgs);

            //Redirect the stderr to the stdout
            pb.redirectErrorStream(true);

            pb.directory(workDir.toFile());

            for (Environment.Variable var:env) {
                pb.environment().put(var.getKey(), var.getValue());
                task.log("Environment variable: " + var.getKey() + "=" + var.getValue(), Project.MSG_VERBOSE);
            }

            //Start the new process
            Process process = pb.start();


            // Adding to log the command
            StringBuilder builder = new StringBuilder();
            for(String s : cmdArgs) {

                builder.append(s);
                //Append space
                builder.append(" ");
            }
            task.log("Executing - " + builder.toString(), task.getCommandLogLevel());


            //Create the StreamGobbler to read the process output
            StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), task);

            outputGobbler.start();

            int exit_value;

            //Wait for the process to finish
            exit_value = process.waitFor();


            return exit_value;

        } catch (InterruptedException e) {

            return -2;
        }

    }

}
