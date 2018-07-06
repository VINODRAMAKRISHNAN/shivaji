def getWorkerNodeDetails(devices) {
	def ienoofworkernodesrequired=0;
	def chromenoofworkernodesrequired=0;
	def appname="";
	
   
    try {
	    devices.each
        {
            if (it.@active !=null && it.@active == "true" ) {
                if ( it.@browserName=="internetexplorer") {
                    if (it.@availableDevices != null && it.@browserName=="internetexplorer" && it.@availableDevices >0 ){
                        ienoofworkernodesrequired=ienoofworkernodesrequired + (it.@availableDevices as Integer)
                    }
                    else{
                        ienoofworkernodesrequired++
                    }
                }
                if ( it.@browserName=="chrome") {
    			    if (it.@availableDevices != null && it.@browserName=="chrome" && it.@availableDevices >0 ){
                        chromenoofworkernodesrequired=chromenoofworkernodesrequired + (it.@availableDevices as Integer)
                    }
                    else{
                        chromenoofworkernodesrequired++
                    }
                }
            }
            it.capability.each{ itm ->
                if (itm.@name != null &&  itm.@name =="applicationName"){
                    if (appname=="" && itm.text() != null && itm.text() != ""){
                        appname=itm.text();
                    }
                }
            }
        }
    }
    catch(Exception testError){
        println('XML parsed failed in getWorkerNodeDetails =' + testError);
    }
	
	def rettuple = new Tuple(ienoofworkernodesrequired,chromenoofworkernodesrequired,appname);
	
	return rettuple
}

def executeCommand(strcommand){
    def standardOut = new StringBuffer(), standardErr = new StringBuffer() 
    def command = strcommand
    def proc = command.execute() 
    proc.consumeProcessOutput(standardOut, standardErr) 
    proc.waitForOrKill(10000) 
    if (standardErr == null || (standardErr !=null && standardErr.toString().trim() == "")){
        standardErr = "No error"
    }
    println('CommandExecutionError=' + standardErr);
    return standardOut.toString();
}

def getRequiredStoppedInstanceIds(ienoofworkernodesrequired,chromenoofworkernodesrequired){
    def totalinstancesrequired=ienoofworkernodesrequired + chromenoofworkernodesrequired
	def strinstanceids = executeCommand("aws ec2 describe-instances --filters Name=tag:InstanceType,Values=SELENIUM-WORKER-WIN10 Name=instance-state-name,Values=stopped --query Reservations[*].Instances[*].InstanceId --output text")
    strinstanceids=strinstanceids.replace(" ","")
    strinstanceids=strinstanceids.replaceAll("(\\r|\\n|\\r\\n)+", ",")
    println('strinstanceids=' + strinstanceids)
   // println('arrinstanceids=' + arrinstanceids.length
   def arrinstanceid = null
   if (strinstanceids != null && strinstanceids != "" ){
       arrinstanceid = strinstanceids.split(",")
       if (arrinstanceid.length > 1){
           return arrinstanceid.take((ienoofworkernodesrequired + chromenoofworkernodesrequired))
       }
       else{
           return []
       }
       
   }
   else{
       return []
   }
}




def startRequiredInstancesByArray(arrinstanceids){
    if (arrinstanceids != null && arrinstanceids.length > 0){
        def strinstanceids= arrinstanceids.join(" ")
        println('Starting the instances')
        def startresult = executeCommand("aws ec2 start-instances --instance-ids " + strinstanceids + "  --output text")
        println('Waiting for instnaces to be started.')
        sh 'aws ec2 wait instance-status-ok --instance-ids ' + strinstanceids        
        def describeresult = executeCommand("aws ec2 describe-instances --instance-ids " + strinstanceids + "  --output text")
        println ('Successfully started the instances, count=' + arrinstanceids.length)
    }
    else{
        println ('No instance available to start, task aborted')
    }
}
def stopRequiredInstancesByString(strinstanceids){
    def arrinstanceids = strinstanceids.split(" ");
    stopRequiredInstancesByArray(arrinstanceids);
}

def stopRequiredInstancesByArray(arrinstanceids){
    if (arrinstanceids != null && arrinstanceids.length > 0){
        def strinstanceids= arrinstanceids.join(" ")
        def stopresult = executeCommand("aws ec2 stop-instances --instance-ids " + strinstanceids + "  --output text")
        println('Waiting for instnaces to be stopped.')
        sh 'aws ec2 wait instance-stopped --instance-ids ' + strinstanceids
        //def stopwaitresult = executeCommand("aws ec2 wait instance-stopped --instance-ids " + strinstanceids + "  --output text")
        def describeresult = executeCommand("aws ec2 describe-instances --instance-ids " + strinstanceids + "  --output text")
        println ('Successfully stopped the instances, count=' + arrinstanceids.length)
    }
    else{
        println ('No instance available to stop, task aborted')
    }
}

def runSSMCommandToConnectWorkerToHub(arrinstanceids,ienoofworkernodesrequired,chromenoofworkernodesrequired){
    println('Executing runSSMCommandToConnectWorkerToHub Starts')
    def instancecount=0;
    if (arrinstanceids != null && arrinstanceids.length > 0 && (arrinstanceids.length==(ienoofworkernodesrequired +  chromenoofworkernodesrequired))){
        for (i = 0; i <chromenoofworkernodesrequired; i++) { 
            def tmpinstid=arrinstanceids[instancecount];
            sh """ 
                aws ssm send-command --document-name AWS-RunPowerShellScript --parameters commands=["C:/ssm/connect-to-hub.bat chrome chromedriver 10.0.2.46 4445 5557 chrome AOS"] --targets Key=instanceids,Values=${tmpinstid}  
                
                """
            instancecount++
            println('Successfully connected the chrome worker instance to the Hub, Instanceid=' + arrinstanceids.length)
        };
        
        for (i = 0; i <ienoofworkernodesrequired; i++) { 
            def tmp2instid=arrinstanceids[instancecount];
            sh """ 
                aws ssm send-command --document-name AWS-RunPowerShellScript --parameters commands=["C:/ssm/connect-to-hub.bat ie IEDriverServer 10.0.2.46 4445 5557 internetexplorer AOS"] --targets Key=instanceids,Values=${tmp2instid}  
                
                """
            instancecount++
            println ('Successfully connected the ie worker instance to the Hub, Instanceid=' + arrinstanceids.length)
        };
    }
    else{
        println ('No worker instance available to connect to the Hub, task aborted')
    }
    println('Executing runSSMCommandToConnectWorkerToHub Ends')
}

return this