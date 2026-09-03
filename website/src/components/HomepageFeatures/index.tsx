import type {ReactNode} from 'react';
import {useEffect, useRef, useState} from 'react';
import clsx from 'clsx';
import Heading from '@theme/Heading';
import styles from './styles.module.css';

type FeatureItem = {
  title: string;
  icon: ReactNode;
  description: ReactNode;
};

const BoltIcon = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
    strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M13 2 3 14h9l-1 8 10-12h-9l1-8z" />
  </svg>
);

const PlugIcon = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
    strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M9 2v6M15 2v6M6 8h12v3a6 6 0 0 1-12 0V8zM12 17v5" />
  </svg>
);

const ShieldIcon = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
    strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M12 3l7 3v5c0 4.5-3 8-7 10-4-2-7-5.5-7-10V6l7-3z" />
    <path d="M9 12l2 2 4-4" />
  </svg>
);

const FeatureList: FeatureItem[] = [
  {
    title: '高约定，开箱即用',
    icon: BoltIcon,
    description: (
      <>
        引入一个 starter 即可获得统一响应、全局异常处理、TraceId 链路等生产基础设施。
        业务代码继续使用普通的 Java / Spring 编程模型。
      </>
    ),
  },
  {
    title: '可插拔，可替换',
    icon: PlugIcon,
    description: (
      <>
        每项能力通过特性开关声明式启停，每个 SPI 都能用一个 <code>@Bean</code> 覆盖为自己的实现。
        限流、幂等、锁、存储的默认实现都可替换为分布式版本。
      </>
    ),
  },
  {
    title: '安全默认，边界清晰',
    icon: ShieldIcon,
    description: (
      <>
        请求加解密、签名、防重放、安全响应头、SQL 防护、文件魔数校验内置且默认安全。
        框架负责基础设施，业务持有领域模型与认证。
      </>
    ),
  },
];

function Feature({title, icon, description, index}: FeatureItem & {index: number}) {
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
        <span className={styles.iconBadge}>{icon}</span>
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
            为什么选择 Coco
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
