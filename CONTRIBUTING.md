# How to contribute

We'd love to accept your patches and contributions to this project. There are
just a few small guidelines you need to follow.

## Contributor License Agreement

Contributions to this project must be accompanied by a Contributor License
Agreement. You (or your employer) retain the copyright to your contribution,
this simply gives us permission to use and redistribute your contributions as
part of the project. Head over to <https://cla.developers.google.com/> to see
your current agreements on file or to sign a new one.

You generally only need to submit a CLA once, so if you've already submitted one
(even if it was for a different project), you probably don't need to do it
again.

## Code reviews

All submissions, including submissions by project members, require review. We
use GitHub pull requests for this purpose. Consult
[GitHub Help](https://help.github.com/articles/about-pull-requests/) for more
information on using pull requests.

## Getting your pull request through
### Testing
An essential part of getting your change through is to make sure all existing tests pass.

#### Prerequisites
* You will need maven installed to build and run tests.
* You will need a GCP project to test on with the compute engine API enabled. Make sure you have all the relevant permissions for your project.
* For development, we recommend IntelliJ.
* **For Windows Images/VM's**, you will need Java and OpenSSH pre-installed. We have suggested startup-scripts for installing both if you do not want to pre-install, but OpenSSH installation tends to hang (this was tested with the Windows 2016 image provided by GCE).


#### Running the tests
* Write/change tests as necessary based on the code changes you made
* Make sure you are at the directory where pom.xml is located

##### Unit Tests
* Simply run the following in your command line

```
mvn test
```

##### Integration Tests
* You will need to set up a couple of environment variables in your command line before you can run the integration tests. 3. and 4. are only required when running a windows integration test

1. GOOGLE_PROJECT_ID
1. GOOGLE_CREDENTIALS
1. GOOGLE_BOOT_DISK_PROJECT_ID
1. GOOGLE_BOOT_DISK_IMAGE_NAME

* Run the following:
```
mvn verify
```

###### Windows Integration Test
* By default, in the pom.xml file, we will exclude running the Windows Integration test unless a windows-related change was made since you will have to build a windows image with packer
* Exclusion is in these couple of lines between the excludes tags:
```
<configuration>
    <disableXmlReport>true</disableXmlReport>
    <useFile>false</useFile>
    <excludes>
        <exclude>**/ComputeEngineCloudWindowsIT.java</exclude>
    </excludes>
</configuration>
```

* If you do make a **windows-related change**, remove the exclusion temporarily and run the integration test with mvn verify
  * GOOGLE_BOOT_DISK_PROJECT_ID will be the same as your project id
  * GOOGLE_BOOT_DISK_IMAGE_NAME will be the name of the image you created using packer in google cloud console
  * More information on building your baseline windows image can be found [here](WINDOWS.md) and an example file can be found [here](windows-it-install.ps1)