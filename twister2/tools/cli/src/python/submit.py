# Copyright 2016 Twitter. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
''' submit.py '''
import glob
import logging
import os
import tempfile

from twister2.common.src.python.utils.log import Log
from twister2.proto import topology_pb2
from twister2.tools.cli.src.python.result import SimpleResult, Status
import twister2.tools.cli.src.python.args as cli_args
import twister2.tools.cli.src.python.execute as execute
import twister2.tools.cli.src.python.jars as jars
import twister2.tools.cli.src.python.opts as opts
import twister2.tools.cli.src.python.result as result
import twister2.tools.common.src.python.utils.config as config
import twister2.tools.common.src.python.utils.classpath as classpath

# pylint: disable=too-many-return-statements

################################################################################
def create_parser(subparsers):
    '''
    Create a subparser for the submit command
    :param subparsers:
    :return:
    '''
    parser = subparsers.add_parser(
        'submit',
        help='Submit a job',
        usage="%(prog)s [options] cluster/[role]/[env] " + \
              "job-file-name job-class-name [job-args]",
        add_help=True
    )

    cli_args.add_titles(parser)
    cli_args.add_cluster_role_env(parser)
    cli_args.add_job_file(parser)
    cli_args.add_job_class(parser)
    cli_args.add_config(parser)
    cli_args.add_extra_launch_classpath(parser)
    cli_args.add_system_property(parser)
    cli_args.add_verbose(parser)

    parser.set_defaults(subcommand='submit')
    return parser


################################################################################
def launch_a_job(cl_args, tmp_dir, job_file, job_defn_file, job_name):
    '''
    Launch a job given job jar, its definition file and configurations
    :param cl_args:
    :param tmp_dir:
    :param job_file:
    :param job_defn_file:
    :return:
    '''
    # get the normalized path for job.tar.gz
    job_pkg_path = config.normalized_class_path(os.path.join(tmp_dir, 'job.tar.gz'))

    # get the release yaml file
    release_yaml_file = config.get_twister2_release_file()

    # create a tar package with the cluster configuration and generated config files
    config_path = cl_args['config_path']
    tar_pkg_files = [job_file, job_defn_file]
    generated_config_files = [release_yaml_file, cl_args['override_config_file']]

    config.create_tar(job_pkg_path, tar_pkg_files, config_path, generated_config_files)

    # pass the args to submitter main
    args = [
        "--cluster", cl_args['cluster'],
        "--twister2_home", config.get_twister2_dir(),
        "--config_path", config_path,
        "--override_config_file", cl_args['override_config_file'],
        "--release_file", release_yaml_file,
        "--job_package", job_pkg_path,
        "--job_defn", job_defn_file,
        "--job_bin", job_file   # pex file if pex specified
    ]

    if Log.getEffectiveLevel() == logging.DEBUG:
        args.append("--verbose")

    if cl_args["dry_run"]:
        args.append("--dry_run")
        if "dry_run_format" in cl_args:
            args += ["--dry_run_format", cl_args["dry_run_format"]]

    lib_jars = config.get_twister2_libs(
        jars.resource_scheduler_jars() + jars.uploader_jars() + jars.statemgr_jars() + jars.task_scheduler_jars()
    )
    extra_jars = cl_args['extra_launch_classpath'].split(':')

    # invoke the submitter to submit and launch the job
    main_class = 'com.twitter.twister2.scheduler.SubmitterMain'
    res = execute.twister2_class(
        class_name=main_class,
        lib_jars=lib_jars,
        extra_jars=extra_jars,
        args=args,
        java_defines=[])
    err_context = "Failed to launch job '%s'" % job_name
    if cl_args["dry_run"]:
        err_context += " in dry-run mode"
    succ_context = "Successfully launched job '%s'" % job_name
    if cl_args["dry_run"]:
        succ_context += " in dry-run mode"
    res.add_context(err_context, succ_context)
    return res

################################################################################
def launch_jobs(cl_args, job_file, tmp_dir):
    '''
    Launch topologies
    :param cl_args:
    :param job_file:
    :param tmp_dir:
    :return: list(Responses)
    '''
    # the submitter would have written the .defn file to the tmp_dir
    defn_files = glob.glob(tmp_dir + '/*.defn')

    if len(defn_files) == 0:
        return SimpleResult(Status.Twister2Error, "No topologies found under %s" % tmp_dir)

    results = []
    for defn_file in defn_files:
        # load the job definition from the file
        job_defn = job_pb2.Topology()
        try:
            handle = open(defn_file, "rb")
            job_defn.ParseFromString(handle.read())
            handle.close()
        except Exception as e:
            err_context = "Cannot load job definition '%s': %s" % (defn_file, e)
            return SimpleResult(Status.Twister2Error, err_context)
        # launch the job
        Log.info("Launching job: \'%s\'", job_defn.name)
        res = launch_a_job(
            cl_args, tmp_dir, job_file, defn_file, job_defn.name)
        results.append(res)
    return results


################################################################################
def submit_fatjar(cl_args, unknown_args, tmp_dir):
    '''
    We use the packer to make a package for the jar and dump it
    to a well-known location. We then run the main method of class
    with the specified arguments. We pass arguments as an environment variable TWISTER2_OPTIONS.

    This will run the jar file with the job_class_name. The submitter
    inside will write out the job defn file to a location that
    we specify. Then we write the job defn file to a well known
    location. We then write to appropriate places in zookeeper
    and launch the scheduler jobs
    :param cl_args:
    :param unknown_args:
    :param tmp_dir:
    :return:
    '''
    # execute main of the job to create the job definition
    job_file = cl_args['job-file-name']

    main_class = cl_args['job-class-name']

    res = execute.twister2_class(
        class_name=main_class,
        lib_jars=config.get_twister2_libs(jars.job_jars()),
        extra_jars=[job_file],
        args=tuple(unknown_args),
        java_defines=cl_args['job_main_jvm_property'])

    result.render(res)

    if not res.is_successful():
        err_context = ("Failed to create job definition " \
                       "file when executing class '%s' of file '%s'") % (main_class, job_file)
        res.add_context(err_context)
        return res

    results = launch_jobs(cl_args, job_file, tmp_dir)

    return results


################################################################################
def submit_tar(cl_args, unknown_args, tmp_dir):
    '''
    Extract and execute the java files inside the tar and then add job
    definition file created by running submitTopology

    We use the packer to make a package for the tar and dump it
    to a well-known location. We then run the main method of class
    with the specified arguments. We pass arguments as an environment variable TWISTER2_OPTIONS.
    This will run the jar file with the job class name.

    The submitter inside will write out the job defn file to a location
    that we specify. Then we write the job defn file to a well known
    packer location. We then write to appropriate places in zookeeper
    and launch the aurora jobs
    :param cl_args:
    :param unknown_args:
    :param tmp_dir:
    :return:
    '''
    # execute main of the job to create the job definition
    job_file = cl_args['job-file-name']
    java_defines = cl_args['job_main_jvm_property']
    main_class = cl_args['job-class-name']
    res = execute.twister2_tar(
        main_class,
        job_file,
        tuple(unknown_args),
        tmp_dir,
        java_defines)

    result.render(res)

    if not res.is_successful():
        err_context = ("Failed to create job definition " \
                       "file when executing class '%s' of file '%s'") % (main_class, job_file)
        res.add_context(err_context)
        return res

    return launch_jobs(cl_args, job_file, tmp_dir)

################################################################################
#  Execute the pex file to create job definition file by running
#  the job's main class.
################################################################################
# pylint: disable=unused-argument
def submit_pex(cl_args, unknown_args, tmp_dir):
    # execute main of the job to create the job definition
    job_file = cl_args['job-file-name']
    job_class_name = cl_args['job-class-name']
    res = execute.twister2_pex(
        job_file, job_class_name, tuple(unknown_args))

    result.render(res)
    if not res.is_successful():
        err_context = ("Failed to create job definition " \
                       "file when executing class '%s' of file '%s'") % (job_class_name, job_file)
        res.add_context(err_context)
        return res

    return launch_jobs(cl_args, job_file, tmp_dir)

################################################################################
# pylint: disable=unused-argument
def run(command, parser, cl_args, unknown_args):
    '''
    Submits the job to the scheduler
      * Depending on the job file name extension, we treat the file as a
        fatjar (if the ext is .jar) or a tar file (if the ext is .tar/.tar.gz).
      * We upload the job file to the packer, update zookeeper and launch
        scheduler jobs representing that job
      * You can see your job in Twister2 UI
    :param command:
    :param parser:
    :param cl_args:
    :param unknown_args:
    :return:
    '''
    # get the job file name
    job_file = cl_args['job-file-name']

    # check to see if the job file exists
    if not os.path.isfile(job_file):
        err_context = "Topology file '%s' does not exist" % job_file
        return SimpleResult(Status.InvocationError, err_context)

    # check if it is a valid file type
    jar_type = job_file.endswith(".jar")
    tar_type = job_file.endswith(".tar") or job_file.endswith(".tar.gz")
    pex_type = job_file.endswith(".pex")
    if not jar_type and not tar_type and not pex_type:
        ext_name = os.path.splitext(job_file)
        err_context = "Unknown file type '%s'. Please use .tar or .tar.gz or .jar or .pex file" \
                      % ext_name
        return SimpleResult(Status.InvocationError, err_context)

    # check if extra launch classpath is provided and if it is validate
    if cl_args['extra_launch_classpath']:
        valid_classpath = classpath.valid_java_classpath(cl_args['extra_launch_classpath'])
        if not valid_classpath:
            err_context = "One of jar or directory in extra launch classpath does not exist: %s" % \
                          cl_args['extra_launch_classpath']
            return SimpleResult(Status.InvocationError, err_context)

    # create a temporary directory for job definition file
    tmp_dir = tempfile.mkdtemp()
    opts.cleaned_up_files.append(tmp_dir)

    # if job needs to be launched in deactivated state, do it so
    if cl_args['deploy_deactivated']:
        initial_state = job_pb2.TopologyState.Name(job_pb2.PAUSED)
    else:
        initial_state = job_pb2.TopologyState.Name(job_pb2.RUNNING)

    # set the tmp dir and deactivated state in global options
    opts.set_config('cmdline.jobdefn.tmpdirectory', tmp_dir)
    opts.set_config('cmdline.job.initial.state', initial_state)

    # check the extension of the file name to see if it is tar/jar file.
    if jar_type:
        return submit_fatjar(cl_args, unknown_args, tmp_dir)
    elif tar_type:
        return submit_tar(cl_args, unknown_args, tmp_dir)
    else:
        return submit_pex(cl_args, unknown_args, tmp_dir)