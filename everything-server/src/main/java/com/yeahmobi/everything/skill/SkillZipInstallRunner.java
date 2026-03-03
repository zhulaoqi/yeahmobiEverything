package com.yeahmobi.everything.skill;

import com.yeahmobi.everything.common.Config;
import com.yeahmobi.everything.repository.mysql.MySQLDatabaseManager;
import com.yeahmobi.everything.repository.mysql.SkillPackageRepository;
import com.yeahmobi.everything.repository.mysql.SkillRepositoryImpl;

import java.nio.file.Path;

/**
 * CLI runner for installing skill ZIP packages.
 *
 * Usage:
 *   java ... SkillZipInstallRunner /path/to/skill.zip [sourceUrl] [actor]
 */
public class SkillZipInstallRunner {

    public static void main(String[] args) throws Exception {
        if (args == null || args.length == 0) {
            System.out.println("Usage: SkillZipInstallRunner <zipPath> [sourceUrl] [actor]");
            return;
        }

        String zipPath = args[0];
        String sourceUrl = args.length > 1 ? args[1] : "";
        String actor = args.length > 2 ? args[2] : "system";

        Config config = Config.getInstance();
        MySQLDatabaseManager mysql = new MySQLDatabaseManager(config);
        mysql.initialize();

        SkillRepositoryImpl skillRepository = new SkillRepositoryImpl(mysql);
        SkillPackageRepository packageRepository = new SkillPackageRepository(mysql);
        SkillZipInstaller installer = new SkillZipInstaller(skillRepository, packageRepository, null);

        SkillZipInstallResult result = installer.installFromZip(Path.of(zipPath), sourceUrl, actor);
        System.out.println("success=" + result.success());
        System.out.println("idempotent=" + result.idempotent());
        System.out.println("skillName=" + result.skillName());
        System.out.println("skillVersion=" + result.skillVersion());
        System.out.println("artifactSha256=" + result.artifactSha256());
        System.out.println("message=" + result.message());

        if (!result.success()) {
            System.exit(2);
        }
    }
}

