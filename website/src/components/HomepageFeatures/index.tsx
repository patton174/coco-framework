import type {ReactNode} from 'react';
import {useEffect, useRef, useState} from 'react';
import clsx from 'clsx';
import Heading from '@theme/Heading';
import Translate from '@docusaurus/Translate';
import {Zap, Blocks, ShieldCheck, type LucideIcon} from 'lucide-react';
import styles from './styles.module.css';

type FeatureItem = {
  title: ReactNode;
  Icon: LucideIcon;
  description: ReactNode;
};

const FeatureList: FeatureItem[] = [
  {
    title: <Translate id="home.feature.convention.title">高约定，开箱即用</Translate>,
    Icon: Zap,
    description: (
      <Translate id="home.feature.convention.desc">
        引入一个 starter 即可获得统一响应、全局异常处理、TraceId 链路等生产基础设施，业务代码继续使用普通的 Java / Spring 编程模型。
      </Translate>
    ),
  },
  {
    title: <Translate id="home.feature.pluggable.title">可插拔，可替换</Translate>,
    Icon: Blocks,
    description: (
      <Translate id="home.feature.pluggable.desc">
        每项能力通过特性开关声明式启停，每个 SPI 都能用一个 @Bean 覆盖为自己的实现。限流、幂等、锁、存储的默认实现都可替换为分布式版本。
      </Translate>
    ),
  },
  {
    title: <Translate id="home.feature.secure.title">安全默认，边界清晰</Translate>,
    Icon: ShieldCheck,
    description: (
      <Translate id="home.feature.secure.desc">
        请求加解密、签名、防重放、安全响应头、SQL 防护、文件魔数校验内置且默认安全。框架负责基础设施，业务持有领域模型与认证。
      </Translate>
    ),
  },
];

function Feature({title, Icon, description, index}: FeatureItem & {index: number}) {
  const ref = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setVisible(true);
          observer.disconnect();
        }
      },
      {threshold: 0.2},
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  return (
    <div className={clsx('col col--4')}>
      <div
        ref={ref}
        className={clsx(styles.card, visible && styles.visible)}
        style={{transitionDelay: `${index * 0.12}s`}}>
        <span className={styles.iconBadge}>
          <Icon size={26} strokeWidth={2} aria-hidden="true" />
        </span>
        <Heading as="h3" className={styles.cardTitle}>
          {title}
        </Heading>
        <p className={styles.cardText}>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures(): ReactNode {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className={styles.sectionHead}>
          <span className={styles.eyebrow}>Why Coco</span>
          <Heading as="h2" className={styles.sectionTitle}>
            <Translate id="home.features.heading">为什么选择 Coco</Translate>
          </Heading>
        </div>
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} index={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
