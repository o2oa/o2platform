package com.x.program.center.jaxrs.captcha;

import com.google.common.collect.Streams;
import com.x.base.core.container.EntityManagerContainer;
import com.x.base.core.container.factory.EntityManagerContainerFactory;
import com.x.base.core.entity.annotation.CheckPersistType;
import com.x.base.core.project.annotation.FieldDescribe;
import com.x.base.core.project.bean.tuple.Pair;
import com.x.base.core.project.http.ActionResult;
import com.x.base.core.project.logger.Logger;
import com.x.base.core.project.logger.LoggerFactory;
import com.x.program.center.ThisApplication;
import com.x.program.center.core.entity.Captcha;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.apache.commons.lang3.ArrayUtils;

class V2Create extends BaseAction {

	private static final Logger LOGGER = LoggerFactory.getLogger(V2Create.class);

	ActionResult<Wo> execute(Integer width, Integer height) throws Exception {
		try (EntityManagerContainer emc = EntityManagerContainerFactory.instance().create()) {
			ActionResult<Wo> result = new ActionResult<>();
			final Integer adjustWidth = (width <= 80 || width > 800) ? 80 : width;
			final Integer adjustHeight = (height <= 30 || height > 800) ? 30 : height;

			Optional<Optional<Pair<String, byte[]>>> optional = Stream.of(kaptcha, mnist)
					.map(o -> o.apply(Pair.of(adjustWidth, adjustHeight))).filter(Optional::isPresent).findFirst();
			if (optional.isEmpty()) {
				throw new ExceptionCreateCaptcha();
			}
			Optional<Pair<String, byte[]>> opt = optional.get();
			if (opt.isEmpty()) {
				throw new ExceptionCreateCaptcha();
			}
			Pair<String, byte[]> pair = opt.get();
			emc.beginTransaction(Captcha.class);
			Captcha captcha = new Captcha();
			captcha.setAnswer(pair.first());
			emc.persist(captcha, CheckPersistType.all);
			emc.commit();
			Wo wo = new Wo();
			wo.setId(captcha.getId());
			wo.setImage(Base64.getEncoder().encodeToString(pair.second()));
			result.setData(wo);
			return result;
		}
	}

	private static Function<Pair<Integer, Integer>, Optional<Pair<String, byte[]>>> kaptcha = param -> Optional.empty();

	private static Function<Pair<Integer, Integer>, Optional<Pair<String, byte[]>>> mnist = param -> {
		Random random = new SecureRandom();
		String verifyCode = random.nextInt(10) + "" + random.nextInt(10) + "" + random.nextInt(10) + ""
				+ random.nextInt(10);
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Character[] chars = ArrayUtils.toObject(verifyCode.toCharArray());
			BufferedImage combined = new BufferedImage(28 * chars.length, 28, BufferedImage.TYPE_INT_RGB);
			final File dir = new File(ThisApplication.context().path(), "WEB-INF/mnist");
			Streams.mapWithIndex(Arrays.<Character>stream(chars), Pair::of).forEach(p -> {
				BufferedImage image;
				try {
					image = ImageIO.read(new File(dir, p.first() + "_" + random.nextInt(10)));
					combined.getGraphics().drawImage(image, (int) (28 * p.second()), 0, null);
				} catch (IOException e) {
					LOGGER.error(e);
				}
			});
			BufferedImage overlay = new BufferedImage(param.first(), param.second(), BufferedImage.TYPE_INT_RGB);
			overlay.createGraphics().drawRenderedImage(combined,
					AffineTransform.getScaleInstance(overlay.getWidth() / (double) combined.getWidth(),
							overlay.getHeight() / (double) combined.getHeight()));
			ImageIO.write(overlay, "png", out);
			return Optional.of(Pair.of(verifyCode, out.toByteArray()));
		} catch (IOException e) {
			LOGGER.error(e);
		}
		return Optional.empty();
	};

	public static class Wo {

		@FieldDescribe("标识")
		private String id;

		@FieldDescribe("图像")
		private String image;

		public String getImage() {
			return image;
		}

		public void setImage(String image) {
			this.image = image;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

	}

}
