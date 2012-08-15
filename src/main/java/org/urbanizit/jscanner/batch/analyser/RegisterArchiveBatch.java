package org.urbanizit.jscanner.batch.analyser;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.urbanizit.jscanner.ArchiveTypes;
import org.urbanizit.jscanner.analyser.resolver.OwnerGroupResolver;
import org.urbanizit.jscanner.analyser.scanner.AbstractArchiveScanner;
import org.urbanizit.jscanner.analyser.scanner.DirectoryScanner;
import org.urbanizit.jscanner.analyser.scanner.factory.ScannerFactory;
import org.urbanizit.jscanner.analyser.scanner.utils.CheckSumUtils;
import org.urbanizit.jscanner.transfert.Archive;
import org.urbanizit.jscanner.transfert.ArchiveCriteria;
import org.urbanizit.jscanner.transfert.NestableArchive;
import org.urbanizit.jscanner.transfert.itf.ArchiveServiceItf;

/**
 * Simple runner to register archives
 * 
 * @author ldassonville
 *
 */
public class RegisterArchiveBatch{

	private static final Logger LOGGER = LoggerFactory.getLogger(RegisterArchive.class);
	
	@Inject private ArchiveServiceItf archiveServiceItfI;
    
	public static void main(String[] args) {
		RegisterArchiveBatch batch = new RegisterArchiveBatch();
		batch.init();
		batch.saveArchives();
		
	}

	public void init(){
		ApplicationContext ctx = new ClassPathXmlApplicationContext("classpath:spring-rmi.xml");
		this.archiveServiceItfI = (ArchiveServiceItf)ctx.getBean("RmiArchiveService");
	}
	
    public void saveArchives(){
    	
    	List<File> files = new DirectoryScanner().scan(new File("P:\\SAS\\SAS\\GestionMedias"));
    	    	
    	try {
	    	Collection<Callable<Long>> registerTasks = new ArrayList<Callable<Long>>();
	    	for (File file : files) {   		
	    		registerTasks.add(new RegisterArchive(file, archiveServiceItfI));
			}  	
	   	
	    	ExecutorService executorService = Executors.newFixedThreadPool(1);
			executorService.invokeAll(registerTasks);		    	
			executorService.shutdown();
    	} catch (InterruptedException e) {
    		LOGGER.error("Error saveArchiveTest",e);
		}    
    }     
    
    /**
     * 
     * 
     * @author ldassonville
     *
     */
    private class RegisterArchive implements Callable<Long>{
   
    	private ArchiveServiceItf archiveService;
    	private File file;
    	
    	public RegisterArchive(final File file, final ArchiveServiceItf archiveService){
    		this.file = file;
    		this.archiveService = archiveService;
    	}
    	
		public Long call() throws Exception {
			
			try{
				LOGGER.debug("start task {}", file.getName());
				
				String checksum = CheckSumUtils.getSha256(new FileInputStream(file));
				ArchiveCriteria criteria = new ArchiveCriteria();
				criteria.setChecksum(checksum);				
				List<Archive> archiveDtoIs = archiveService.findArchiveByCriteria(criteria);
				
				
				
				if(archiveDtoIs == null || archiveDtoIs.isEmpty()){				
					Integer archiveType = ArchiveTypes.getArchiveType(file.getName());				
		    		AbstractArchiveScanner<? extends Archive> scanner = ScannerFactory.getArchiveScanner(archiveType);    		
		    		Archive archive  = scanner.scan(file);   		
		    		applyOwnerGroups(archive);
		    		
		        	return archiveService.registerArchive(archive);
				}else{
					return archiveDtoIs.get(0).getId();
				}
			}catch (Exception e) {
				LOGGER.error("Error will registering archive", e);
				throw e;
			}
		}
    }
    

	public void applyOwnerGroups(Archive archive)throws Exception{
					
		archive.setOwnerGroup(OwnerGroupResolver.resolveOwner(archive));
		
		if(archive instanceof NestableArchive){
			NestableArchive	nestableArchive = (NestableArchive)archive;
			
			Collection<Archive> nestedArchives = nestableArchive.getSubArchives();
			
			if(nestedArchives != null){				
				for (Archive nestedArchive : nestedArchives) {
					applyOwnerGroups(nestedArchive);
				}				
			}			
		}
	}
	
}
